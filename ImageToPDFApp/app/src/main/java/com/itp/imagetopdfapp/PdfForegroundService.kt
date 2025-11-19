package com.itp.imagetopdfapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PdfForegroundService : Service() {

    override fun onBind(intent: Intent?) = null

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PDF_CHANNEL_ID,
                "PDF 변환 채널",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        // 1) 이미지 리스트 가져오기
        val images = intent?.getParcelableArrayListExtra<Uri>("images") ?: emptyList()

        Log.d("d", "이미지 리스트 가져오기")

        // 2) 상단바 진행중 알림
//        val notification = NotificationCompat.Builder(this, MainActivity.CHANNEL_ID)
//            .setContentTitle("PDF 생성 중…")
//            .setContentText("이미지를 PDF로 변환하는 중이에요!")
//            .setSmallIcon(android.R.drawable.stat_sys_download)
//            .setOngoing(true)
//            .setProgress(0, 0, true)
//            .build()
        val notification = NotificationCompat.Builder(this, PDF_CHANNEL_ID)
            .setContentTitle("PDF 생성 중…")
            .setContentText("이미지를 PDF로 변환하는 중이에요!")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()

        // 상단바 고정
        startForeground(1, notification)

        // 3) 백그라운드에서 PDF 생성
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pdfFile = PdfGenerator.generatePdf(this@PdfForegroundService, images)

                // 4) 완료 알림
                val done = NotificationCompat.Builder(this@PdfForegroundService, PDF_CHANNEL_ID)
                    .setContentTitle("PDF 저장 완료")
                    .setContentText("PDF 파일이 다운로드 폴더에 저장되었어요!")
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setAutoCancel(true)
                    .build()

                val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(2, done)

            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 5) 상단바 알림 제거 + 서비스 종료
            stopForeground(true)
            stopSelf()
        }

        return START_NOT_STICKY
    }
}
