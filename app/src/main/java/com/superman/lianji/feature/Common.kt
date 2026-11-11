package com.superman.lianji.feature

import androidx.lifecycle.ViewModel

/** 各页面只依赖 AppContainer，用这个工厂把参数带进 ViewModel。 */
fun <T : ViewModel> simpleFactory(create: () -> T): androidx.lifecycle.ViewModelProvider.Factory =
    object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <R : ViewModel> create(modelClass: Class<R>): R {
            @Suppress("UNCHECKED_CAST")
            return create() as R
        }
    }
