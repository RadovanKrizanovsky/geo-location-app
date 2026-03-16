package com.example.myapplication

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.myapplication.databinding.FragmentChangePasswordBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ChangePasswordFragment : Fragment(R.layout.fragment_change_password) {
    private var binding: FragmentChangePasswordBinding? = null
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding = FragmentChangePasswordBinding.bind(view).apply {
            lifecycleOwner = viewLifecycleOwner
            fragment = this@ChangePasswordFragment
        }
    }
    
    fun onChangePasswordClick() {
        binding?.let { b ->
            val currentPassword = b.etCurrentPassword.text.toString()
            val newPassword = b.etNewPassword.text.toString()
            val confirmPassword = b.etConfirmPassword.text.toString()
            
            if (currentPassword.isBlank()) {
                Snackbar.make(b.root, getString(R.string.error_current_password_empty), Snackbar.LENGTH_SHORT).show()
                return
            }
            
            if (newPassword.isBlank()) {
                Snackbar.make(b.root, getString(R.string.error_new_password_empty), Snackbar.LENGTH_SHORT).show()
                return
            }
            
            if (newPassword != confirmPassword) {
                Snackbar.make(b.root, getString(R.string.error_passwords_mismatch), Snackbar.LENGTH_SHORT).show()
                return
            }
            
            if (newPassword.length < 6) {
                Snackbar.make(b.root, getString(R.string.error_password_too_short), Snackbar.LENGTH_SHORT).show()
                return
            }
            
            lifecycleScope.launch {
                val repository = DataRepository.getInstance(requireContext())
                val error = repository.apiChangePassword(currentPassword, newPassword)
                
                if (error.isEmpty()) {
                    Snackbar.make(
                        b.root,
                        getString(R.string.snackbar_password_changed),
                        Snackbar.LENGTH_LONG
                    ).show()
                    
                    findNavController().navigateUp()
                } else {
                    Snackbar.make(b.root, error, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }
    
    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
