package com.example.restaurantepos.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.restaurantepos.data.PosDao

class PosViewModelFactory(private val dao: PosDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PosViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PosViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}