package com.ulanmaduo.desktop

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.io.File
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

class Character3DView(context: Context) : GLSurfaceView(context) {
    private val characterRenderer = CharacterRenderer()

    fun setModelFile(path: String?) {
        queueEvent { characterRenderer.setModelFile(path) }
        requestRender()
    }

    fun setCasualMode(enabled: Boolean) {
        characterRenderer.casualMode = enabled
        requestRender()
    }

    fun setAction(action: String) {
        characterRenderer.action = action
        requestRender()
    }

    init {
        setEGLContextClientVersion(2)
        setRenderer(characterRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        setZOrderOnTop(false)
    }

    private class CharacterRenderer : Renderer {
        private var program = 0
        private var pos = 0
        private var normal = 0
        private var mvp = 0
        private var model = 0
        private var color = 0
        private lateinit var vertices: FloatBuffer
        private var vertexCount = 0
        private val projection = FloatArray(16)
        private val view = FloatArray(16)
        private val modelMatrix = FloatArray(16)
        private val mv = FloatArray(16)
        private val mvpMatrix = FloatArray(16)
        private val temp = FloatArray(16)
        private var start = System.nanoTime()
        @Volatile private var modelPath: String? = null
        @Volatile private var glbModel: GlbModel? = null

        fun setModelFile(path: String?) {
            modelPath = path
            glbModel = try { if (path.isNullOrBlank()) null else GlbModel.load(File(path)) } catch (_: Exception) { null }
            if (program != 0) glbModel?.createGl()
        }
        @Volatile var casualMode: Boolean = false
        @Volatile var action: String = "idle"

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0.94f, 0.91f, 0.86f, 1f)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glEnable(GLES20.GL_CULL_FACE)
            val vs = """
                attribute vec3 aPosition;
                attribute vec3 aNormal;
                uniform mat4 uMvp;
                uniform mat4 uModel;
                varying vec3 vNormal;
                varying vec3 vPosition;
                void main() {
                    vec4 world = uModel * vec4(aPosition, 1.0);
                    vPosition = world.xyz;
                    vNormal = normalize(mat3(uModel) * aNormal);
                    gl_Position = uMvp * vec4(aPosition, 1.0);
                }
            """.trimIndent()
            val fs = """
                precision mediump float;
                uniform vec4 uColor;
                varying vec3 vNormal;
                varying vec3 vPosition;
                void main() {
                    vec3 light = normalize(vec3(-0.35, 0.8, 0.65));
                    float diffuse = max(dot(normalize(vNormal), light), 0.0);
                    float shade = 0.58 + 0.42 * diffuse;
                    gl_FragColor = vec4(uColor.rgb * shade, uColor.a);
                }
            """.trimIndent()
            program = GLES20.glCreateProgram().also {
                GLES20.glAttachShader(it, compile(GLES20.GL_VERTEX_SHADER, vs))
                GLES20.glAttachShader(it, compile(GLES20.GL_FRAGMENT_SHADER, fs))
                GLES20.glLinkProgram(it)
            }
            pos = GLES20.glGetAttribLocation(program, "aPosition")
            normal = GLES20.glGetAttribLocation(program, "aNormal")
            mvp = GLES20.glGetUniformLocation(program, "uMvp")
            model = GLES20.glGetUniformLocation(program, "uModel")
            color = GLES20.glGetUniformLocation(program, "uColor")
            val data = makeSphere(22, 16)
            vertices = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            vertices.put(data).position(0)
            vertexCount = data.size / 6
            start = System.nanoTime()
            glbModel = try { modelPath?.let { GlbModel.load(File(it)) } } catch (_: Exception) { null }
            glbModel?.createGl()
        }

        private fun compile(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            return shader
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
            Matrix.perspectiveM(projection, 0, 34f, width.toFloat() / height.coerceAtLeast(1), 0.1f, 30f)
            Matrix.setLookAtM(view, 0, 0f, 1.28f, 5.7f, 0f, 1.12f, 0f, 0f, 1f, 0f)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            val t = (System.nanoTime() - start) / 1_000_000_000.0
            glbModel?.let { it.draw(view, projection, t.toFloat(), action); return }
            // Empty model slot: render only the preview background until a user imports a GLB.
            return
        }

        private fun part(
            x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float,
            r: Float, g: Float, b: Float,
            rx: Float = 0f, ry: Float = 0f, rz: Float = 0f, extraY: Float = 1f
        ) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, x, y, z)
            if (rx != 0f) Matrix.rotateM(modelMatrix, 0, rx, 1f, 0f, 0f)
            if (ry != 0f) Matrix.rotateM(modelMatrix, 0, ry, 0f, 1f, 0f)
            if (rz != 0f) Matrix.rotateM(modelMatrix, 0, rz, 0f, 0f, 1f)
            Matrix.scaleM(modelMatrix, 0, sx, sy * extraY, sz)
            Matrix.multiplyMM(mv, 0, view, 0, modelMatrix, 0)
            Matrix.multiplyMM(mvpMatrix, 0, projection, 0, mv, 0)
            GLES20.glUniformMatrix4fv(mvp, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(model, 1, false, modelMatrix, 0)
            GLES20.glUniform4f(color, r, g, b, 1f)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
        }

        private fun makeSphere(stacks: Int, slices: Int): FloatArray {
            val out = ArrayList<Float>()
            fun vertex(lat: Double, lon: Double) {
                val x = (cos(lat) * cos(lon)).toFloat()
                val y = sin(lat).toFloat()
                val z = (cos(lat) * sin(lon)).toFloat()
                out.add(x); out.add(y); out.add(z)
                out.add(x); out.add(y); out.add(z)
            }
            for (i in 0 until stacks) {
                val a0 = -Math.PI / 2 + Math.PI * i / stacks
                val a1 = -Math.PI / 2 + Math.PI * (i + 1) / stacks
                for (j in 0 until slices) {
                    val b0 = 2 * Math.PI * j / slices
                    val b1 = 2 * Math.PI * (j + 1) / slices
                    vertex(a0, b0); vertex(a1, b0); vertex(a1, b1)
                    vertex(a0, b0); vertex(a1, b1); vertex(a0, b1)
                }
            }
            return out.toFloatArray()
        }
    }
}
