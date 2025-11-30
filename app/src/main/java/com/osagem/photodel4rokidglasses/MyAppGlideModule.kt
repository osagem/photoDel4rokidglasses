package com.osagem.photodel4rokidglasses


import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule

@GlideModule
class MyAppGlideModule : AppGlideModule() {
    // 暂时没有配置，但为了保留文件结构
    override fun isManifestParsingEnabled(): Boolean {
        return false
    }
}