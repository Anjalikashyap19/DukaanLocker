package com.iadv.dukaanlocker

enum class ToastType { SUCCESS, ERROR, INFO, WARNING }

data class ToastMessage(
    val id: Long = System.currentTimeMillis(),
    val message: String,
    val type: ToastType = ToastType.INFO,
    val duration: Long = 3000
)