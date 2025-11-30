package com.osagem.photodel4rokidglasses

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewPropertyAnimator
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.osagem.photodel4rokidglasses.databinding.ActivityMainBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    // --- 私有属性 ---
    private lateinit var binding: ActivityMainBinding
    private val fullSloganText: String by lazy { getString(R.string.main_slogan) }
    private val animators = mutableListOf<ViewPropertyAnimator>()
    private var hasAnimationPlayed = false

    // --- 生命周期方法 ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 视图和窗口初始化
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setupFullScreenMode()

        // 设置内容和监听器
        setupWindowInsetsListener() // <--- 提取UI设置逻辑
        setupClickListeners()      // <--- 提取监听器设置逻辑

        // 启动开屏效果
        binding.mainVersion.text = getString(R.string.version_text_format, BuildConfig.VERSION_NAME)
        // 只有在 Activity 首次创建时才播放开屏动画，savedInstanceState 在首次创建时为 null，在从后台恢复时非 null
        if (savedInstanceState == null) {
            // 首次启动，播放动画
            startTypingEffect()
        } else {
            // 非首次启动 (例如从 PhotoListActivity 返回)，指令其直接显示最终状态
            binding.mainSlogan.text = fullSloganText
            showButtonsWithFadeIn()
        }
    }

    // 清理资源
    override fun onDestroy() {
        super.onDestroy()
        // 移除点击监听器，防止内存泄露
        binding.buttonEnter.setOnClickListener(null)
        binding.buttonExitapp.setOnClickListener(null)

        // 遍历列表，统一取消所有正在运行的动画
        animators.forEach { it.cancel() }
        animators.clear() // 清理列表
    }

    // --- 私有辅助方法：UI 设置 ---
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

    // 设置窗口内边距监听器，以适应系统栏（如状态栏、导航栏）
    private fun setupWindowInsetsListener() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    //统一设置所有点击事件监听器
    private fun setupClickListeners() {
        binding.buttonEnter.setOnClickListener {
            val intent = Intent(this, PhotoListActivity::class.java)
            startActivity(intent)
        }
        binding.buttonExitapp.setOnClickListener {
            finishAndRemoveTask()
        }
    }

    // --- 私有辅助方法：动画效果 ---
    private fun startTypingEffect() {
        // 为打字效果准备，先清空文本
        binding.mainSlogan.text = ""

        // 确保按钮和版本textview在开始时不可见
        binding.mainVersion.visibility = View.GONE
        binding.buttonExitapp.visibility = View.GONE
        binding.buttonEnter.visibility = View.GONE

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (!hasAnimationPlayed) {
                    try {
                        fullSloganText.forEach { char ->
                            binding.mainSlogan.append(char.toString())
                            delay(TYPING_DELAY)
                        }
                    } finally {
                        // 只有当协程仍然是活跃状态时，才执行UI操作
                        if (isActive) {
                            showButtonsWithFadeIn()
                            hasAnimationPlayed = true
                        }
                    }
                } else {
                    // 如果动画已经播放过，如从下一页返回时，直接显示最终状态，不播放动画
                    binding.mainSlogan.text = fullSloganText
                    showButtonsWithFadeIn()
                }
            }
        }
    }

    // 在顶部文字打字效果结束后，使用淡入动态效果显示版本信息和主功能按钮
    private fun showButtonsWithFadeIn() {
        // 调用 fadeIn 函数，并将返回的 animator 添加到列表中
        animators.add(binding.mainVersion.fadeIn(FADE_IN_DURATION))
        animators.add(binding.buttonExitapp.fadeIn(FADE_IN_DURATION))

        // 对 buttonEnter 调用 fadeIn，并传入 onEnd 回调
        animators.add(binding.buttonEnter.fadeIn(FADE_IN_DURATION) {
            // 动画结束后，如果 Activity 仍然存活，则让 "进入" 按钮获得焦点
            if (!isFinishing && !isDestroyed) {
                binding.buttonEnter.requestFocus()
            }
        })
    }

}