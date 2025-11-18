package com.itp.imagetopdfapp.viewModel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadManager
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

    @OptIn(UnstableApi::class)
    fun convertPdf(
        context: Context,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = generatePdf(context)
            result.onSuccess { onSuccess(it) }
                .onFailure { onError(it.message ?: "오류") }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun generatePdf(context: Context): Result<File> = withContext(Dispatchers.IO) {
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

    //            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    //            val file = File(downloads, "pdf_${System.currentTimeMillis()}.pdf")
    //
    //            FileOutputStream(file).use { pdf.writeTo(it) }

            val file = savePdfToDownloads(context, pdf)
            lastPdf = file
            Result.success(file)
            pdf.close()

            lastPdf = file

            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        } as Result<File>
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun savePdfToDownloads(context: Context, pdfDocument: PdfDocument): File? = withContext(Dispatchers.IO) {
        val fileName = "pdf_${System.currentTimeMillis()}.pdf"

        val contentValues = android.content.ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val uri = resolver.insert(collection, contentValues) ?: return@withContext null

        resolver.openOutputStream(uri)?.use { output ->
            pdfDocument.writeTo(output)
        }

        contentValues.clear()
        contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)

        File(context.getExternalFilesDir(null), fileName)
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

    fun openPdf(context: Context) {
        lastPdf?.let { file ->
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }

            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "PDF를 열 수 있는 앱이 없어요!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun sharePdf(context: Context) {
        lastPdf?.let { file ->
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(Intent.createChooser(intent, "PDF 공유"))
        }
    }

}