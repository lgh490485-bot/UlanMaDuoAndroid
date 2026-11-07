package com.ulanmaduo.desktop

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

class Character3DView(context: Context) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(2)
        setRenderer(CharacterRenderer())
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
            GLES20.glUseProgram(program)
            vertices.position(0)
            GLES20.glEnableVertexAttribArray(pos)
            GLES20.glEnableVertexAttribArray(normal)
            GLES20.glVertexAttribPointer(pos, 3, GLES20.GL_FLOAT, false, 24, vertices)
            vertices.position(3)
            GLES20.glVertexAttribPointer(normal, 3, GLES20.GL_FLOAT, false, 24, vertices)
            val t = (System.nanoTime() - start) / 1_000_000_000.0
            val breath = 1f + 0.012f * sin(t * 2.0).toFloat()
            val wave = (sin(t * 1.7) * 5.0).toFloat()
            val blinkPhase = (t % 4.3)
            val blink = if (blinkPhase > 3.92 && blinkPhase < 4.08) 0.18f else 1f

            // Hair silhouette and long side locks
            part(0f, 1.92f, -0.08f, 0.48f, 0.48f, 0.38f, 0.16f, 0.10f, 0.09f)
            part(-0.34f, 1.53f, -0.02f, 0.13f, 0.68f, 0.15f, 0.15f, 0.09f, 0.08f)
            part(0.34f, 1.53f, -0.02f, 0.13f, 0.68f, 0.15f, 0.15f, 0.09f, 0.08f)
            part(0f, 1.88f, 0.04f, 0.49f, 0.33f, 0.40f, 0.18f, 0.12f, 0.10f)
            part(-0.23f, 1.77f, 0.34f, 0.22f, 0.12f, 0.10f, 0.18f, 0.12f, 0.10f)
            part(0.02f, 1.79f, 0.38f, 0.23f, 0.12f, 0.10f, 0.18f, 0.12f, 0.10f)
            part(0.24f, 1.76f, 0.32f, 0.18f, 0.12f, 0.10f, 0.18f, 0.12f, 0.10f)

            // Face, neck and facial details
            part(0f, 1.55f, 0.18f, 0.34f, 0.43f, 0.29f, 0.94f, 0.73f, 0.62f)
            part(0f, 1.19f, 0.03f, 0.13f, 0.18f, 0.13f, 0.91f, 0.69f, 0.57f)
            part(-0.13f, 1.59f, 0.445f, 0.035f, 0.045f, 0.025f, 0.12f, 0.10f, 0.09f, 0f, 0f, 0f, blink)
            part(0.13f, 1.59f, 0.445f, 0.035f, 0.045f, 0.025f, 0.12f, 0.10f, 0.09f, 0f, 0f, 0f, blink)
            part(0f, 1.46f, 0.455f, 0.055f, 0.018f, 0.018f, 0.63f, 0.28f, 0.28f)

            // Battle suit torso, collar and chest plates
            part(0f, 0.91f, 0f, 0.39f, 0.48f * breath, 0.23f, 0.12f, 0.24f, 0.30f)
            part(0f, 1.13f, 0.20f, 0.22f, 0.12f, 0.08f, 0.80f, 0.78f, 0.69f)
            part(0f, 0.83f, 0.235f, 0.20f, 0.20f, 0.055f, 0.70f, 0.72f, 0.68f)
            part(0f, 0.59f, 0f, 0.32f, 0.16f, 0.21f, 0.08f, 0.15f, 0.20f)

            // Shoulder guards and arms
            part(-0.43f, 1.13f, 0f, 0.19f, 0.18f, 0.19f, 0.72f, 0.74f, 0.69f)
            part(0.43f, 1.13f, 0f, 0.19f, 0.18f, 0.19f, 0.72f, 0.74f, 0.69f)
            part(-0.52f, 0.91f, 0f, 0.13f, 0.29f, 0.14f, 0.12f, 0.24f, 0.30f, 0f, 0f, -8f)
            part(-0.57f, 0.62f, 0.01f, 0.11f, 0.25f, 0.12f, 0.88f, 0.68f, 0.57f, 0f, 0f, 5f)
            part(-0.59f, 0.38f, 0.03f, 0.12f, 0.11f, 0.11f, 0.94f, 0.73f, 0.62f)
            part(0.52f, 0.92f, 0f, 0.13f, 0.29f, 0.14f, 0.12f, 0.24f, 0.30f, 0f, 0f, 8f + wave)
            part(0.57f, 0.64f, 0.02f, 0.11f, 0.24f, 0.12f, 0.88f, 0.68f, 0.57f, 0f, 0f, -8f - wave)
            part(0.59f, 0.40f, 0.04f, 0.12f, 0.11f, 0.11f, 0.94f, 0.73f, 0.62f)

            // Legs and boots
            part(-0.18f, 0.27f, 0f, 0.17f, 0.38f, 0.17f, 0.10f, 0.19f, 0.24f, 0f, 0f, 2f)
            part(0.18f, 0.27f, 0f, 0.17f, 0.38f, 0.17f, 0.10f, 0.19f, 0.24f, 0f, 0f, -2f)
            part(-0.18f, -0.08f, 0.06f, 0.19f, 0.15f, 0.28f, 0.08f, 0.11f, 0.14f)
            part(0.18f, -0.08f, 0.06f, 0.19f, 0.15f, 0.28f, 0.08f, 0.11f, 0.14f)

            GLES20.glDisableVertexAttribArray(pos)
            GLES20.glDisableVertexAttribArray(normal)
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
