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
import android.content.Intent
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    //添加text的打字机逐个字符显示效果
    private lateinit var sloganTextView: TextView
    private val fullSloganText: String by lazy { getString(R.string.main_slogan) }
    private var sloganIndex = 0
    private val typingDelay = 60L // 每个字符显示的延迟时间 (毫秒)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var typeRunnable: Runnable

    private lateinit var buttonEnter: MaterialButton
    private lateinit var buttonExitApp: MaterialButton
    private val fadeInDuration = 500L // 淡入动画的持续时间 (毫秒)
    private lateinit var versionTextView: TextView
    private lateinit var buttonTakePhoto: MaterialButton // 新增：拍照按钮
    // 定义相机应用的包名和请求码
    private val CAMERA_PACKAGE_NAME = "com.android.camera2"
    private val TAKE_PHOTO_REQUEST = 1

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

        buttonTakePhoto = findViewById(R.id.buttonTakePhoto) // 新增：初始化拍照按钮
        versionTextView = findViewById(R.id.main_version) // 初始化 versionTextView

        // 找到 main_version TextView
        //val versionTextView: TextView = findViewById(R.id.main_version)
        // 从自动生成的 BuildConfig 中获取版本名称
        val versionName = BuildConfig.VERSION_NAME
        // 将版本名称设置到 TextView 的文本中
        //versionTextView.text = "v$versionName by osagem"
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

        // 新增：为拍照按钮设置点击监听器
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
        buttonTakePhoto.visibility = View.VISIBLE // 新增

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

        // 新增：为 buttonTakePhoto 创建淡入动画
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
        // 创建一个显式 Intent 来启动特定的相机应用
        val takePictureIntent = packageManager.getLaunchIntentForPackage(CAMERA_PACKAGE_NAME)
        if (takePictureIntent != null) {
            // "android.media.action.STILL_IMAGE_CAMERA" Action 通常用于打开相机应用到拍照模式
            takePictureIntent.action = android.provider.MediaStore.ACTION_IMAGE_CAPTURE

            // "android.intent.extra.quickCapture" 为 true 时，会尝试“拍完即走”，
            // 但为了保证在低内存设备上也能成功保存，我们不强制要求相机拍完后立即退出。
            // 相反，我们使用 startActivityForResult 等待返回结果，这更可靠。
            takePictureIntent.putExtra("android.intent.extra.quickCapture", true)

            try {
                // 使用 startActivityForResult 启动相机，并等待返回结果
                startActivityForResult(takePictureIntent, TAKE_PHOTO_REQUEST)
            } catch (e: Exception) {
                // 如果启动失败（例如应用未安装），给出提示
                Toast.makeText(this, "无法启动相机应用，请检查是否已安装。", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        } else {
            // 如果找不到对应的包名，给出提示
            Toast.makeText(this, "未找到指定的相机应用 ($CAMERA_PACKAGE_NAME)", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == TAKE_PHOTO_REQUEST) {
            if (resultCode == RESULT_OK) {
                // 通常，当 resultCode 是 RESULT_OK 时，表示照片已成功拍摄并保存。
                // 我们可以在这里给用户一个肯定的反馈。
                Toast.makeText(this, "照片拍摄成功", Toast.LENGTH_SHORT).show()
            } else {
                // 如果用户取消了拍照或发生了其他错误，resultCode 将不是 RESULT_OK。
                Toast.makeText(this, "拍照已取消或失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 移除回调以防止内存泄漏
        handler.removeCallbacks(typeRunnable)
    }
}