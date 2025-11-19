package com.osagem.photoview4rokidglasses

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.View
import android.content.Intent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.osagem.photoview4rokidglasses.databinding.ActivityMainBinding

private const val TYPING_DELAY = 60L // 每个字符显示的延迟时间 (毫秒)
private const val FADE_IN_DURATION = 500L // 淡入动画的持续时间 (毫秒)
class MainActivity : AppCompatActivity() {

    // 声明一个 lateinit 的 binding 变量
    private lateinit var binding: ActivityMainBinding

    // 定义常量和懒加载属性
    private val fullSloganText: String by lazy { getString(R.string.main_slogan) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 使用 View Binding 来初始化和设置内容视图
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 对根视图设置窗口内边距监听
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // --- 开始全屏代码 ---
        setupFullScreenMode()

        // 通过 binding 对象安全地访问视图
        // 从自动生成的 BuildConfig 中获取版本名称
        binding.mainVersion.text = "v${BuildConfig.VERSION_NAME} by osagem"

        // 定义打字机效果
        startTypingEffect()

        // 设置点击事件监听
        binding.buttonEnter.setOnClickListener {
            val intent = Intent(this, PhotoListActivity::class.java)
            startActivity(intent)
        }
        binding.buttonExitapp.setOnClickListener {
            finishAndRemoveTask()
        }
    }

    // 设置真全屏
    private fun setupFullScreenMode() {
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
    }

    // 开始打字机效果
    private fun startTypingEffect() {
        // 为打字效果准备
        binding.mainSlogan.text = "" // 清空文本，以便重新开始

        // 确保按钮和版本textview在开始时不可见
        binding.mainVersion.visibility = View.GONE
        binding.buttonExitapp.visibility = View.GONE
        binding.buttonEnter.visibility = View.GONE

        lifecycleScope.launch {
            fullSloganText.forEach { char ->
                binding.mainSlogan.append(char.toString())
                delay(TYPING_DELAY)
            }
            // 协程执行完毕，说明打字机效果完成
            showButtonsWithFadeIn()
        }
    }

    //使用淡入动画显示按钮和版本信息
    private fun showButtonsWithFadeIn() {
        // 将视图的初始 alpha 设为0，然后设为可见，为淡入做准备
        binding.mainVersion.apply {
            alpha = 0f
            visibility = View.VISIBLE
        }
        binding.buttonExitapp.apply {
            alpha = 0f
            visibility = View.VISIBLE
        }
        binding.buttonEnter.apply {
            alpha = 0f
            visibility = View.VISIBLE
        }

        // 执行动画
        binding.mainVersion.animate()
            .alpha(1f)
            .setDuration(FADE_IN_DURATION)
            .start()

        binding.buttonExitapp.animate()
            .alpha(1f)
            .setDuration(FADE_IN_DURATION)
            .start()

        binding.buttonEnter.animate()
            .alpha(1f)
            .setDuration(FADE_IN_DURATION)
            .withEndAction {
                // 动画结束后，让 "进入" 按钮获得焦点
                binding.buttonEnter.requestFocus()
            }
            .start()
    }

    // 如有需要清理的资源，就放在这里
    override fun onDestroy() {
        super.onDestroy()
    }
}