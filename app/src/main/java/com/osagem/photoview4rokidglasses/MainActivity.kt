package com.osagem.photoview4rokidglasses

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.view.View
import android.animation.ObjectAnimator
import android.content.ContentValues
import android.content.Intent
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MainActivity : AppCompatActivity() {

    //添加text的打字机逐个字符显示效果
    private lateinit var sloganTextView: TextView
    private val fullSloganText: String by lazy { getString(R.string.main_slogan) }
    private var sloganIndex = 0
    private val typingDelay = 60L // 每个字符显示的延迟时间 (毫秒)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var typeRunnable: Runnable
    private var centeredToast: Toast? = null
    private lateinit var buttonEnter: MaterialButton
    private lateinit var buttonExitApp: MaterialButton
    private val fadeInDuration = 500L // 淡入动画的持续时间 (毫秒)
    private lateinit var versionTextView: TextView
    private lateinit var buttonTakePhoto: MaterialButton // 新增：拍照按钮
    // 定义相机应用的包名和请求码
    private val CAMERA_PACKAGE_NAME = "com.android.camera2"
    // 用于存储拍照后图片的 URI
    private var photoUri: Uri? = null
    //private val TAKE_PHOTO_REQUEST = 1

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (photoUri != null && fileExists(photoUri!!)) {
            showCenteredToast(getString(R.string.toast_take_photo_and_save_done))
            // 通知媒体库扫描新文件，使其出现在相册中
            Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).also { mediaScanIntent ->
                mediaScanIntent.data = photoUri
                sendBroadcast(mediaScanIntent)
            }
        } else {
            showCenteredToast(getString(R.string.toast_take_photo_cancelled_or_failed))

        }
    }

    private fun showCenteredToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        centeredToast?.cancel()
        centeredToast = Toast.makeText(this, message, duration).apply {
            setGravity(android.view.Gravity.CENTER, 0, 120)
            show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 让内容布局扩展到系统栏（状态栏和导航栏）后面
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // 获取 WindowInsetsController
        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView) ?: return
        // 隐藏状态栏和导航栏
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        // 设置交互行为：当从屏幕边缘滑动时，系统栏会短暂显示
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // --- 结束全屏代码 ---

        // 获取 TextView 实例
        sloganTextView = findViewById(R.id.main_slogan)
        buttonEnter = findViewById(R.id.buttonEnter)
        buttonExitApp = findViewById(R.id.buttonExitapp)

        buttonTakePhoto = findViewById(R.id.buttonTakePhoto) // 初始化拍照按钮
        versionTextView = findViewById(R.id.main_version) // 初始化 versionTextView

        // 从自动生成的 BuildConfig 中获取版本名称
        val versionName = BuildConfig.VERSION_NAME
        // 将版本名称设置到 TextView 的文本中
        versionTextView.text = "v$versionName by osagem"

        // 初始化时清空 TextView
        sloganTextView.text = ""
        // 定义打字机效果的 Runnable
        typeRunnable = object : Runnable {
            override fun run() {
                if (sloganIndex < fullSloganText.length) {
                    sloganTextView.append(fullSloganText[sloganIndex].toString())
                    sloganIndex++
                    handler.postDelayed(this, typingDelay)
                } else {
                    // 打字机效果完成
                    showButtonsWithFadeIn()
                }
            }
        }

        startTypingEffect()

        buttonEnter.setOnClickListener {
            val intent = Intent(this, PhotoListActivity::class.java)
            startActivity(intent)
        }

        buttonExitApp.setOnClickListener {
            finishAndRemoveTask()
        }

        // 为拍照按钮设置点击监听器
        buttonTakePhoto.setOnClickListener {
            dispatchTakePictureIntent()
        }
    }
    private fun startTypingEffect() {
        // 为打字效果准备
        sloganIndex = 0 // 重置索引
        sloganTextView.text = "" // 清空文本，以便重新开始
        // 确保按钮和版本textview在开始时不可见和完全透明，以便淡入效果从透明开始
        versionTextView.alpha = 0f
        versionTextView.visibility = View.GONE
        buttonExitApp.alpha = 0f
        buttonExitApp.visibility = View.GONE
        buttonEnter.alpha = 0f
        buttonEnter.visibility = View.GONE
        buttonTakePhoto.alpha = 0f // 新增
        buttonTakePhoto.visibility = View.GONE // 新增
        handler.postDelayed(typeRunnable, typingDelay)
    }
    private fun showButtonsWithFadeIn() {
        // 将界面上两个按钮和一个版本TextView设置为可见，但保持透明
        versionTextView.alpha = 0f
        versionTextView.visibility = View.VISIBLE // 然后设置为可见
        buttonExitApp.alpha = 0f
        buttonExitApp.visibility = View.VISIBLE // 然后设置为可见
        buttonEnter.alpha = 0f
        buttonEnter.visibility = View.VISIBLE // 然后设置为可见
        buttonTakePhoto.alpha = 0f // 新增
        buttonTakePhoto.visibility = View.VISIBLE // 然后设置为可见

        // 为 versionTextView 创建淡入动画
        ObjectAnimator.ofFloat(versionTextView, "alpha", 0f, 1f).apply {
            duration = fadeInDuration
            start()
        }

        // 为 buttonExitApp 创建淡入动画
        ObjectAnimator.ofFloat(buttonExitApp, "alpha", 0f, 1f).apply {
            duration = fadeInDuration
            start()
        }

        // 为 buttonTakePhoto 创建淡入动画
        ObjectAnimator.ofFloat(buttonTakePhoto, "alpha", 0f, 1f).apply {
            duration = fadeInDuration
            start()
        }

        // 为 buttonEnter 创建淡入动画
        ObjectAnimator.ofFloat(buttonEnter, "alpha", 0f, 1f).apply {
            duration = fadeInDuration
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // 动画结束后，让 "进入" 按钮获得焦点，方便用户直接点击进入
                    buttonEnter.requestFocus()
                }
            })
            start()
        }

    }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = packageManager.getLaunchIntentForPackage(CAMERA_PACKAGE_NAME)
        if (takePictureIntent == null) {
            //Toast.makeText(this, "未找到指定的相机应用 ($CAMERA_PACKAGE_NAME)", Toast.LENGTH_LONG).show()
            showCenteredToast("${getString(R.string.toast_spec_camera_app_not_found)}: $CAMERA_PACKAGE_NAME")
            return
        }

        // 为照片创建一个 URI
        photoUri = try {
            createImageUri()
        } catch (ex: Exception) {
            //Toast.makeText(this, "创建图片URI失败: ${ex.message}", Toast.LENGTH_SHORT).show()
            showCenteredToast("${getString(R.string.toast_failed_to_create_image_URI)}: ${ex.message}")
            null
        }

        photoUri?.also { uri ->
            // 将目标 URI 传递给相机应用
            takePictureIntent.action = MediaStore.ACTION_IMAGE_CAPTURE
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
            // 授予相机应用对该 URI 的写权限
            takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

            try {
                takePictureLauncher.launch(takePictureIntent)
            } catch (e: Exception) {
                showCenteredToast(getString(R.string.toast_camera_app_cannot_launched))
                e.printStackTrace()
            }
        }
    }

    private fun createImageUri(): Uri? {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "img-${timeStamp}.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, imageFileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            // 在 Android 10 (API 29) 及以上版本，使用这个相对路径
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/Camera")
            } else {
                // 在 Android 9 (API 28) 上，需要提供绝对路径
                val imageDir = Environment.getExternalStoragePublicDirectory("${Environment.DIRECTORY_DCIM}/Camera")
                if (!imageDir.exists()) {
                    imageDir.mkdirs()
                }
                val imageFile = File(imageDir, imageFileName)
                put(MediaStore.Images.Media.DATA, imageFile.absolutePath)
            }
        }
        // 通过 MediaStore 插入一个新的图片条目，并获取其 content URI
        // 这个 URI 是相机应用写入图片数据的目标
        return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    }

    //检查给定的 content URI 对应的文件是否存在且有内容
    private fun fileExists(uri: Uri): Boolean {
        return try {
            contentResolver.openInputStream(uri)?.use { it.read() != -1 } ?: false
        } catch (e: Exception) {
            false
        }
    }

    override fun onDestroy() {
        centeredToast?.cancel()
        super.onDestroy()
        // 移除回调以防止内存泄漏
        handler.removeCallbacks(typeRunnable)
    }
}