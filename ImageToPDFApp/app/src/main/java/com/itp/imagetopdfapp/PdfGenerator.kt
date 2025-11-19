package com.itp.imagetopdfapp

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder.decodeBitmap
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import kotlin.math.min

object PdfGenerator {

    @RequiresApi(Build.VERSION_CODES.Q)
    public fun generatePdf(
        context: Context,
        imageUris: List<Uri>
        ): File {

        val pdf = PdfDocument()

        imageUris.forEachIndexed { index, uri ->
            val bitmap = decodeBitmapSafely(context, uri)
                ?: throw Exception("이미지 디코딩 실패: $uri")

            val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
            val page = pdf.startPage(pageInfo)
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            pdf.finishPage(page)
            bitmap.recycle()
        }

        val saved = saveToDownloads(context, pdf)
        pdf.close()

        return saved
    }

    private fun decodeBitmapSafely(context: Context, uri: Uri): Bitmap? {
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

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToDownloads(
        context: Context,
        pdfDocument: PdfDocument
    ): File {
        val fileName = "pdf_${System.currentTimeMillis()}.pdf"

        val contentValues = ContentValues().apply{
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val collection =
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val uri =
            resolver.insert(collection, contentValues)
                ?: throw Exception("다운로드 폴더에 파일 생성 실패")

        resolver.openOutputStream(uri)?.use { output ->
            pdfDocument.writeTo(output)
        }

        return File(context.getExternalFilesDir(null), fileName)
    }
}