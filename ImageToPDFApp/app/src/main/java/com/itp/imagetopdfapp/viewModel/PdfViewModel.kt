package com.itp.imagetopdfapp.viewModel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itp.imagetopdfapp.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

class PdfViewModel : ViewModel() {
    var selectedImages by mutableStateOf<List<Uri>>(emptyList())
    private set

    var lastPdf: File? = null

    fun updateImages(images: List<Uri>) {
        selectedImages = images
    }

    fun convertPdf(onSuccess: (File) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = generatePdf()
            result.onSuccess { onSuccess(it) }
                .onFailure{ onError(it.message ?: "오류") }
        }
    }

    private suspend fun generatePdf(): Result<File> = withContext(Dispatchers.IO) {
        val pdf = PdfDocument()

        try {
            selectedImages.forEachIndexed { index, uri ->
                val bitmap = decodeBitmapSafely(uri) ?: return@withContext Result.failure(
                    Exception("이미지 불러오기 실패")
                )

                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                val page = pdf.startPage(pageInfo)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdf.finishPage(page)

                bitmap.recycle()
            }

            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloads, "pdf_${System.currentTimeMillis()}.pdf")

            FileOutputStream(file).use { pdf.writeTo(it) }
            pdf.close()

            lastPdf = file

            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun decodeBitmapSafely(uri: Uri): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply{
                inPreferredConfig = Bitmap.Config.RGB_565
                inJustDecodeBounds = true
            }

            App.instance.contentResolver.openInputStream(uri)?.use{
                BitmapFactory.decodeStream(it, null, options)

            }

            options.inSampleSize = calculateInSampleSize(options, 1080, 1920)
            options.inJustDecodeBounds = false

            App.instance.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqW: Int, reqH: Int): Int {
        val (h, w) = options.outHeight to options.outWidth
        var size = 1

        if (h > reqH || w > reqW) {
            size = min(h / reqH, w / reqW)
        }

        return size
    }


}