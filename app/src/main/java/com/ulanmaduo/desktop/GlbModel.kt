package com.ulanmaduo.desktop

import android.opengl.GLES20
import android.opengl.Matrix
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.sqrt

/** Minimal glTF 2.0 binary mesh renderer for the bundled/imported UlanMaDuo GLB. */
class GlbModel private constructor(private val meshes: List<Mesh>, private val joints: List<Joint>, private val inverseBind: FloatArray) {
    private data class Joint(val name: String, val parent: Int, val tx: Float, val ty: Float, val tz: Float)
    private val jointGlobals = FloatArray(joints.size * 16)
    private val skinMatrices = FloatArray(joints.size * 16)
    private val jointLocal = FloatArray(16)
    private val jointTemp = FloatArray(16)
    private val jointParent = FloatArray(16)
    private data class Mesh(val name: String, val vertices: FloatBuffer, val colors: FloatBuffer, val indices: ShortBuffer, val count: Int, val pivotX: Float, val pivotY: Float, val pivotZ: Float, val base: FloatArray, val jointIds: FloatArray, val weights: FloatArray, val deformed: FloatArray = FloatArray(base.size))
    private var program = 0
    private var position = 0
    private var normal = 0
    private var color = 0
    private var mvp = 0
    private var model = 0
    private val baseMatrix = FloatArray(16)
    private val localMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    fun createGl() {
        val vs = """
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec4 aColor;
            uniform mat4 uMvp;
            uniform mat4 uModel;
            varying vec3 vNormal;
            varying vec4 vColor;
            void main() {
              vNormal = normalize(mat3(uModel) * aNormal);
              vColor = aColor;
              gl_Position = uMvp * vec4(aPosition, 1.0);
            }
        """.trimIndent()
        val fs = """
            precision mediump float;
            varying vec3 vNormal;
            varying vec4 vColor;
            void main() {
              vec3 light = normalize(vec3(-0.35, 0.8, 0.65));
              float diffuse = max(dot(normalize(vNormal), light), 0.0);
              float shade = 0.68 + 0.32 * diffuse;
              gl_FragColor = vec4(vColor.rgb * shade, vColor.a);
            }
        """.trimIndent()
        fun shader(type: Int, source: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, source)
            GLES20.glCompileShader(s)
            return s
        }
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, shader(GLES20.GL_VERTEX_SHADER, vs))
            GLES20.glAttachShader(it, shader(GLES20.GL_FRAGMENT_SHADER, fs))
            GLES20.glLinkProgram(it)
        }
        position = GLES20.glGetAttribLocation(program, "aPosition")
        normal = GLES20.glGetAttribLocation(program, "aNormal")
        color = GLES20.glGetAttribLocation(program, "aColor")
        mvp = GLES20.glGetUniformLocation(program, "uMvp")
        model = GLES20.glGetUniformLocation(program, "uModel")
    }

    fun draw(view: FloatArray, projection: FloatArray, elapsedSeconds: Float, action: String) {
        if (program == 0) return
        Matrix.setIdentityM(baseMatrix, 0)
        // Face the preview camera: authored model front is oriented toward -Z.
        Matrix.rotateM(baseMatrix, 0, 180f, 0f, 1f, 0f)
        val breathing = kotlin.math.sin(elapsedSeconds * 2.0f) * 0.006f
        Matrix.translateM(baseMatrix, 0, 0f, breathing, 0f)
        val sway = when (action) {
            "wave" -> kotlin.math.sin(elapsedSeconds * 3.2f) * 1.2f
            "walk" -> kotlin.math.sin(elapsedSeconds * 6.0f) * 0.8f
            else -> kotlin.math.sin(elapsedSeconds * 1.3f) * 0.35f
        }
        Matrix.rotateM(baseMatrix, 0, sway, 0f, 1f, 0f)
        updateSkin(elapsedSeconds, action)
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glEnableVertexAttribArray(normal)
        GLES20.glEnableVertexAttribArray(color)

        for (mesh in meshes) {
            val angle = if (joints.isEmpty()) jointAngle(mesh.name, elapsedSeconds, action) else 0f
            Matrix.setIdentityM(localMatrix, 0)
            Matrix.translateM(localMatrix, 0, mesh.pivotX, mesh.pivotY, mesh.pivotZ)
            if (angle != 0f) Matrix.rotateM(localMatrix, 0, angle, 0f, 0f, 1f)
            Matrix.translateM(localMatrix, 0, -mesh.pivotX, -mesh.pivotY, -mesh.pivotZ)
            Matrix.multiplyMM(modelMatrix, 0, baseMatrix, 0, localMatrix, 0)
            Matrix.multiplyMM(mvpMatrix, 0, view, 0, modelMatrix, 0)
            Matrix.multiplyMM(mvpMatrix, 0, projection, 0, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(mvp, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(model, 1, false, modelMatrix, 0)
            mesh.vertices.position(0)
            GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 24, mesh.vertices)
            mesh.vertices.position(3)
            GLES20.glVertexAttribPointer(normal, 3, GLES20.GL_FLOAT, false, 24, mesh.vertices)
            mesh.colors.position(0)
            GLES20.glVertexAttribPointer(color, 4, GLES20.GL_FLOAT, false, 16, mesh.colors)
            mesh.indices.position(0)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.count, GLES20.GL_UNSIGNED_SHORT, mesh.indices)
        }
        GLES20.glDisableVertexAttribArray(position)
        GLES20.glDisableVertexAttribArray(normal)
        GLES20.glDisableVertexAttribArray(color)
    }


    /** True glTF vertex skinning using four weighted joints per vertex. */
    private fun updateSkin(time: Float, action: String) {
        if (joints.isEmpty()) return
        for (i in joints.indices) {
            val j = joints[i]
            Matrix.setIdentityM(jointLocal, 0)
            Matrix.translateM(jointLocal, 0, j.tx, j.ty, j.tz)
            val gait = kotlin.math.sin(time * 6f)
            val wave = kotlin.math.sin(time * 5f)
            val angle = when (j.name) {
                "upper_arm_R" -> if (action == "wave") -58f + wave * 11f else if (action == "walk") gait * 22f else 0f
                "forearm_R" -> if (action == "wave") -38f + wave * 9f else if (action == "walk") -gait * 14f else 0f
                "upper_arm_L" -> if (action == "walk") -gait * 22f else 0f
                "forearm_L" -> if (action == "walk") gait * 14f else 0f
                "thigh_R" -> if (action == "sit") 68f else if (action == "walk") gait * 24f else 0f
                "thigh_L" -> if (action == "sit") -68f else if (action == "walk") -gait * 24f else 0f
                "shin_R" -> if (action == "sit") -72f else if (action == "walk") -(12f + kotlin.math.max(0f,gait)*22f) else 0f
                "shin_L" -> if (action == "sit") 72f else if (action == "walk") 12f + kotlin.math.max(0f,gait)*22f else 0f
                "head" -> kotlin.math.sin(time*1.2f)*1.2f
                "hair_L" -> kotlin.math.sin(time*2.1f)*2f
                "hair_R" -> -kotlin.math.sin(time*2.1f)*2f
                else -> 0f
            }
            // Rotate around anatomically appropriate axes in the converted Y-up space.
            if (angle != 0f) {
                when (j.name) {
                    "thigh_R", "thigh_L", "shin_R", "shin_L", "head", "hair_L", "hair_R" ->
                        Matrix.rotateM(jointLocal, 0, angle, 1f, 0f, 0f)
                    else -> Matrix.rotateM(jointLocal, 0, angle, 0f, 0f, 1f)
                }
            }
            val off = i * 16
            if (j.parent >= 0) {
                System.arraycopy(jointGlobals, j.parent * 16, jointParent, 0, 16)
                Matrix.multiplyMM(jointTemp, 0, jointParent, 0, jointLocal, 0)
                System.arraycopy(jointTemp, 0, jointGlobals, off, 16)
            } else System.arraycopy(jointLocal, 0, jointGlobals, off, 16)
            Matrix.multiplyMM(skinMatrices, off, jointGlobals, off, inverseBind, off)
        }
        for (mesh in meshes) {
            val out = mesh.deformed
            for (v in 0 until mesh.base.size / 6) {
                val px=mesh.base[v*6]; val py=mesh.base[v*6+1]; val pz=mesh.base[v*6+2]
                val nx=mesh.base[v*6+3]; val ny=mesh.base[v*6+4]; val nz=mesh.base[v*6+5]
                var x=0f; var y=0f; var z=0f; var ox=0f; var oy=0f; var oz=0f
                for (k in 0..3) {
                    val w=mesh.weights[v*4+k]
                    if (w <= 0f) continue
                    val jo=mesh.jointIds[v*4+k].toInt().coerceIn(0,joints.size-1)*16
                    val m=skinMatrices
                    x+=(m[jo]*px+m[jo+4]*py+m[jo+8]*pz+m[jo+12])*w
                    y+=(m[jo+1]*px+m[jo+5]*py+m[jo+9]*pz+m[jo+13])*w
                    z+=(m[jo+2]*px+m[jo+6]*py+m[jo+10]*pz+m[jo+14])*w
                    ox+=(m[jo]*nx+m[jo+4]*ny+m[jo+8]*nz)*w
                    oy+=(m[jo+1]*nx+m[jo+5]*ny+m[jo+9]*nz)*w
                    oz+=(m[jo+2]*nx+m[jo+6]*ny+m[jo+10]*nz)*w
                }
                out[v*6]=x; out[v*6+1]=y; out[v*6+2]=z
                val len=sqrt(ox*ox+oy*oy+oz*oz).coerceAtLeast(0.00001f)
                out[v*6+3]=ox/len; out[v*6+4]=oy/len; out[v*6+5]=oz/len
            }
            mesh.vertices.position(0)
            mesh.vertices.put(out)
            mesh.vertices.position(0)
        }
    }

    /**
     * Name-based rigid-part rig for this authored mesh. This is procedural joint
     * animation, not glTF skinning: each named part rotates around an authored pivot.
     */
    private fun jointAngle(name: String, time: Float, action: String): Float {
        val wave = kotlin.math.sin(time * 5.0f)
        val walk = kotlin.math.sin(time * 6.0f)
        val isRight = name.endsWith("_R")
        val isLeft = name.endsWith("_L")
        if (name.startsWith("upper_arm_R") || name == "pauldron_R") {
            return when (action) {
                "wave" -> -58f + wave * 11f
                "walk" -> walk * 22f
                else -> 0f
            }
        }
        if (name.startsWith("elbow_R") || name.startsWith("forearm_R") || name.startsWith("glove_R")) {
            return when (action) {
                "wave" -> -38f + wave * 9f
                "walk" -> -walk * 14f
                else -> 0f
            }
        }
        if (name.startsWith("upper_arm_L") || name == "pauldron_L") {
            return if (action == "walk") -walk * 22f else 0f
        }
        if (name.startsWith("elbow_L") || name.startsWith("forearm_L") || name.startsWith("glove_L")) {
            return if (action == "walk") walk * 14f else 0f
        }
        if (name.startsWith("thigh_")) {
            return when (action) {
                "walk" -> (if (isRight) 1f else -1f) * walk * 24f
                "sit" -> if (isRight) 68f else -68f
                else -> 0f
            }
        }
        if (name.startsWith("shin_") || name.startsWith("knee_guard_")) {
            return when (action) {
                "walk" -> (if (isRight) -1f else 1f) * (12f + kotlin.math.max(0f, walk) * 22f)
                "sit" -> if (isRight) -72f else 72f
                else -> 0f
            }
        }
        if (name.startsWith("boot_")) {
            return when (action) {
                "walk" -> (if (isRight) -1f else 1f) * (8f + kotlin.math.max(0f, walk) * 16f)
                "sit" -> if (isRight) -72f else 72f
                else -> 0f
            }
        }
        if (name.startsWith("long_hair_")) return kotlin.math.sin(time * 2.1f) * 1.4f * (if (isRight) 1f else -1f)
        return 0f
    }

    companion object {
        fun load(file: File): GlbModel {
            val bytes = file.readBytes()
            require(bytes.size >= 20 && bytes[0] == 0x67.toByte() && bytes[1] == 0x6c.toByte()) { "不是有效的 GLB 文件" }
            val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            header.position(12)
            val jsonLength = header.int
            val jsonType = header.int
            require(jsonType == 0x4E4F534A && jsonLength > 0 && 20 + jsonLength <= bytes.size) { "GLB JSON 区块无效" }
            val root = JSONObject(String(bytes, 20, jsonLength, Charsets.UTF_8).trimEnd('\u0000', ' '))
            val binHeader = 20 + jsonLength
            val binLength = ByteBuffer.wrap(bytes, binHeader, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val binStart = binHeader + 8
            require(binStart + binLength <= bytes.size) { "GLB 模型数据不完整" }
            val bin = ByteBuffer.wrap(bytes, binStart, binLength).slice().order(ByteOrder.LITTLE_ENDIAN)
            val views = root.getJSONArray("bufferViews")
            val accessors = root.getJSONArray("accessors")
            fun accessor(index: Int): JSONObject = accessors.getJSONObject(index)
            fun view(index: Int): JSONObject = views.getJSONObject(index)
            fun floatData(index: Int, components: Int): FloatArray {
                val a = accessor(index); val v = view(a.getInt("bufferView"))
                val count = a.getInt("count")
                val stride = v.optInt("byteStride", components * 4)
                val offset = v.optInt("byteOffset", 0) + a.optInt("byteOffset", 0)
                val out = FloatArray(count * components)
                for (i in 0 until count) for (c in 0 until components) {
                    out[i * components + c] = bin.getFloat(offset + i * stride + c * 4)
                }
                return out
            }
            fun colorData(index: Int, count: Int): FloatArray {
                val a = accessor(index); val v = view(a.getInt("bufferView"))
                val stride = v.optInt("byteStride", 4)
                val offset = v.optInt("byteOffset", 0) + a.optInt("byteOffset", 0)
                val out = FloatArray(count * 4)
                for (i in 0 until count) for (c in 0 until 4) {
                    out[i * 4 + c] = (bin.get(offset + i * stride + c).toInt() and 255) / 255f
                }
                return out
            }
            fun indexData(index: Int): IntArray {
                val a = accessor(index); val v = view(a.getInt("bufferView"))
                val count = a.getInt("count")
                val offset = v.optInt("byteOffset", 0) + a.optInt("byteOffset", 0)
                return IntArray(count) { i ->
                    when (a.getInt("componentType")) {
                        5125 -> bin.getInt(offset + i * 4)
                        5123 -> bin.getShort(offset + i * 2).toInt() and 65535
                        5121 -> bin.get(offset + i).toInt() and 255
                        else -> error("不支持的索引格式")
                    }
                }
            }
            val meshesJson = root.getJSONArray("meshes")
            val nodes = root.getJSONArray("nodes")
            val skins = root.optJSONArray("skins")
            val jointNodeIds = if (skins != null && skins.length() > 0) skins.getJSONObject(0).getJSONArray("joints") else org.json.JSONArray()
            val jointMap = HashMap<Int, Int>()
            for (i in 0 until jointNodeIds.length()) jointMap[jointNodeIds.getInt(i)] = i
            val parentMap = HashMap<Int, Int>()
            for (i in 0 until nodes.length()) {
                val children = nodes.getJSONObject(i).optJSONArray("children") ?: continue
                for (j in 0 until children.length()) parentMap[children.getInt(j)] = i
            }
            val rig = ArrayList<Joint>()
            for (i in 0 until jointNodeIds.length()) {
                val nodeId = jointNodeIds.getInt(i)
                val node = nodes.getJSONObject(nodeId)
                val tr = node.optJSONArray("translation")
                val parentNode = parentMap[nodeId] ?: -1
                rig.add(Joint(node.optString("name").removePrefix("Bone_"), jointMap[parentNode] ?: -1,
                    tr?.optDouble(0, 0.0)?.toFloat() ?: 0f,
                    tr?.optDouble(1, 0.0)?.toFloat() ?: 0f,
                    tr?.optDouble(2, 0.0)?.toFloat() ?: 0f))
            }
            // Convert joint translations and inverse-bind matrices from source Z-up
            // into the renderer's Y-up basis: (x, y, z) -> (x, z, -y).
            for (i in rig.indices) {
                val j = rig[i]
                rig[i] = j.copy(tx = j.tx, ty = j.tz, tz = -j.ty)
            }
            val inverseBind = if (skins != null && skins.length() > 0 && skins.getJSONObject(0).has("inverseBindMatrices")) {
                val source = floatData(skins.getJSONObject(0).getInt("inverseBindMatrices"), 16)
                val c = FloatArray(16)
                Matrix.setIdentityM(c, 0)
                c[5] = 0f; c[6] = -1f; c[9] = 1f; c[10] = 0f
                val invC = FloatArray(16)
                Matrix.setIdentityM(invC, 0)
                invC[5] = 0f; invC[6] = 1f; invC[9] = -1f; invC[10] = 0f
                val converted = FloatArray(source.size)
                val tempMatrix = FloatArray(16)
                val resultMatrix = FloatArray(16)
                for (i in rig.indices) {
                    val offset = i * 16
                    val matrix = source.copyOfRange(offset, offset + 16)
                    Matrix.multiplyMM(tempMatrix, 0, c, 0, matrix, 0)
                    Matrix.multiplyMM(resultMatrix, 0, tempMatrix, 0, invC, 0)
                    System.arraycopy(resultMatrix, 0, converted, offset, 16)
                }
                converted
            } else FloatArray(0)
            val meshOrder = ArrayList<Int>()
            for (i in 0 until nodes.length()) {
                val node = nodes.getJSONObject(i)
                if (node.has("mesh")) meshOrder.add(node.getInt("mesh"))
            }
            if (meshOrder.isEmpty()) for (i in 0 until meshesJson.length()) meshOrder.add(i)
            fun byteVec4(index: Int, normalized: Boolean): FloatArray {
                val a = accessor(index); val v = view(a.getInt("bufferView"))
                val count = a.getInt("count"); val stride = v.optInt("byteStride", 4)
                val offset = v.optInt("byteOffset", 0) + a.optInt("byteOffset", 0)
                val out = FloatArray(count * 4)
                for (i in 0 until count) for (c in 0 until 4) {
                    val value = bin.get(offset + i * stride + c).toInt() and 255
                    out[i * 4 + c] = if (normalized) value / 255f else value.toFloat()
                }
                return out
            }
            val result = ArrayList<Mesh>()
            val nodeNames = HashMap<Int, String>()
            for (i in 0 until nodes.length()) { val node = nodes.getJSONObject(i); if (node.has("mesh")) nodeNames[node.getInt("mesh")] = node.optString("name", "mesh_${node.getInt("mesh")}") }
            for (meshIndex in meshOrder) {
                val meshName = nodeNames[meshIndex] ?: "mesh_$meshIndex"
                val primitives = meshesJson.getJSONObject(meshIndex).getJSONArray("primitives")
                for (p in 0 until primitives.length()) {
                    val primitive = primitives.getJSONObject(p)
                    val attrs = primitive.getJSONObject("attributes")
                    val raw = floatData(attrs.getInt("POSITION"), 3)
                    val vertexCount = raw.size / 3
                    val idx = indexData(primitive.getInt("indices"))
                    require(vertexCount < 65536 && idx.all { it in 0 until vertexCount }) { "模型网格索引超出范围" }
                    val packed = FloatArray(vertexCount * 6)
                    for (i in 0 until vertexCount) {
                        packed[i * 6] = raw[i * 3]
                        packed[i * 6 + 1] = raw[i * 3 + 2]
                        packed[i * 6 + 2] = -raw[i * 3 + 1]
                    }
                    // Accumulate smooth vertex normals from indexed triangles.
                    for (i in idx.indices step 3) {
                        val a = idx[i]; val b = idx[i + 1]; val c = idx[i + 2]
                        val ax = packed[a * 6]; val ay = packed[a * 6 + 1]; val az = packed[a * 6 + 2]
                        val ux = packed[b * 6] - ax; val uy = packed[b * 6 + 1] - ay; val uz = packed[b * 6 + 2] - az
                        val vx = packed[c * 6] - ax; val vy = packed[c * 6 + 1] - ay; val vz = packed[c * 6 + 2] - az
                        val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
                        for (k in intArrayOf(a, b, c)) {
                            packed[k * 6 + 3] += nx; packed[k * 6 + 4] += ny; packed[k * 6 + 5] += nz
                        }
                    }
                    for (i in 0 until vertexCount) {
                        val o = i * 6 + 3
                        val length = sqrt(packed[o] * packed[o] + packed[o + 1] * packed[o + 1] + packed[o + 2] * packed[o + 2]).coerceAtLeast(0.00001f)
                        packed[o] /= length; packed[o + 1] /= length; packed[o + 2] /= length
                    }
                    val colors = colorData(attrs.getInt("COLOR_0"), vertexCount)
                    val jointIds = if (attrs.has("JOINTS_0")) byteVec4(attrs.getInt("JOINTS_0"), false) else FloatArray(vertexCount * 4)
                    val weights = if (attrs.has("WEIGHTS_0")) byteVec4(attrs.getInt("WEIGHTS_0"), true) else FloatArray(vertexCount * 4).also { a -> for (v in 0 until vertexCount) a[v * 4] = 1f }
                    val baseVertices = packed.copyOf()
                    val shorts = ShortArray(idx.size) { idx[it].toShort() }
                    fun directFloats(data: FloatArray): FloatBuffer = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(data); position(0) }
                    val indexBuffer = ByteBuffer.allocateDirect(shorts.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(shorts); position(0) }
                    val pivotX = when { meshName.endsWith("_R") && (meshName.startsWith("upper_arm") || meshName.startsWith("pauldron")) -> 0.39f; meshName.endsWith("_L") && (meshName.startsWith("upper_arm") || meshName.startsWith("pauldron")) -> -0.39f; meshName.endsWith("_R") && (meshName.startsWith("elbow") || meshName.startsWith("forearm") || meshName.startsWith("glove")) -> 0.47f; meshName.endsWith("_L") && (meshName.startsWith("elbow") || meshName.startsWith("forearm") || meshName.startsWith("glove")) -> -0.47f; meshName.endsWith("_R") -> 0.17f; meshName.endsWith("_L") -> -0.17f; else -> 0f }
                    val pivotY = when { meshName.startsWith("upper_arm") || meshName.startsWith("pauldron") -> 1.53f; meshName.startsWith("elbow") || meshName.startsWith("forearm") || meshName.startsWith("glove") -> 1.25f; meshName.startsWith("thigh") -> 1.20f; meshName.startsWith("shin") || meshName.startsWith("knee_guard") -> 0.64f; meshName.startsWith("boot") -> 0.20f; else -> 0f }
                    result.add(Mesh(meshName, directFloats(packed), directFloats(colors), indexBuffer, shorts.size, pivotX, pivotY, 0f, baseVertices, jointIds, weights))
                }
            }
            require(result.isNotEmpty()) { "GLB 中没有可显示的网格" }
            require(rig.isEmpty() || inverseBind.size == rig.size * 16) { "骨骼逆绑定矩阵数量不匹配" }
            return GlbModel(result, rig, inverseBind)
        }
    }
}
