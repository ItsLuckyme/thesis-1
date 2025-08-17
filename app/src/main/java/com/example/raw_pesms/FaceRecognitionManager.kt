package com.example.raw_pesms

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class FaceRecognitionManager(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val inputSize = 160
    private val outputSize = 512

    companion object {
        private const val TAG = "FaceRecognitionManager"
        private const val MODEL_FILE = "facenet.tflite"
    }

    init {
        try {
            Log.d(TAG, "Loading FaceNet model...")
            val model = loadModelFile(MODEL_FILE)
            interpreter = Interpreter(model)
            Log.d(TAG, "FaceNet model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load FaceNet model", e)
            throw Exception("Failed to load FaceNet model: ${e.message}")
        }
    }

    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun detectFaces(bitmap: Bitmap): List<Bitmap> {
        Log.d(TAG, "Detecting faces in bitmap: ${bitmap.width}x${bitmap.height}")

        // For simplicity, we'll assume the entire image contains a face
        // In a real app, you'd use ML Kit Face Detection here
        val faces = mutableListOf<Bitmap>()

        try {
            // Resize the bitmap to the required input size
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            faces.add(resizedBitmap)
            Log.d(TAG, "Face detection completed. Assumed 1 face in image.")
        } catch (e: Exception) {
            Log.e(TAG, "Error during face detection", e)
        }

        return faces
    }

    fun embed(faceBitmap: Bitmap): FloatArray? {
        if (interpreter == null) {
            Log.e(TAG, "Interpreter not initialized")
            return null
        }

        try {
            Log.d(TAG, "Generating embedding for face: ${faceBitmap.width}x${faceBitmap.height}")

            // Prepare input
            val input = preprocessImage(faceBitmap)

            // Prepare output
            val output = Array(1) { FloatArray(outputSize) }

            // Run inference
            interpreter!!.run(input, output)

            Log.d(TAG, "Face embedding generated successfully: ${output[0].size} dimensions")
            return output[0]

        } catch (e: Exception) {
            Log.e(TAG, "Error generating face embedding", e)
            return null
        }
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        // Ensure bitmap is the correct size
        val resizedBitmap = if (bitmap.width != inputSize || bitmap.height != inputSize) {
            Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        } else {
            bitmap
        }

        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(inputSize * inputSize)
        resizedBitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

        var pixel = 0
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val pixelValue = intValues[pixel++]

                // Extract RGB values and normalize to [-1, 1] range
                // This is the standard preprocessing for FaceNet
                byteBuffer.putFloat(((pixelValue shr 16) and 0xFF) / 127.5f - 1.0f) // Red
                byteBuffer.putFloat(((pixelValue shr 8) and 0xFF) / 127.5f - 1.0f)  // Green
                byteBuffer.putFloat((pixelValue and 0xFF) / 127.5f - 1.0f)          // Blue
            }
        }

        return byteBuffer
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        Log.d(TAG, "FaceRecognitionManager closed")
    }
}