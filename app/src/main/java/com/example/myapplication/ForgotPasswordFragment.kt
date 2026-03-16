package com.example.myapplication

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.myapplication.databinding.FragmentForgotPasswordBinding
import com.google.android.material.snackbar.Snackbar

class ForgotPasswordFragment : Fragment(R.layout.fragment_forgot_password) {
    private var binding: FragmentForgotPasswordBinding? = null
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding = FragmentForgotPasswordBinding.bind(view).apply {
            lifecycleOwner = viewLifecycleOwner
            fragment = this@ForgotPasswordFragment
        }
    }
    
    fun onResetPasswordClick() {
        binding?.let { b ->
            val email = b.etEmailForgot.text.toString()
            
            if (email.isBlank()) {
                Snackbar.make(b.root, "Zadajte email", Snackbar.LENGTH_SHORT).show()
                return
            }
            
            Snackbar.make(
                b.root,
                "Link na obnovenie hesla bol odoslaný na $email",
                Snackbar.LENGTH_LONG
            ).show()

            findNavController().navigateUp()
        }
    }
    
    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
