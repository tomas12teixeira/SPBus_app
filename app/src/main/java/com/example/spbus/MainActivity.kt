package com.example.spbus

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
    }

    private fun buildScreen(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(56), dp(24), dp(24))
            setBackgroundColor(Color.parseColor("#F4F7F8"))
        }

        val title = TextView(this).apply {
            text = "SPBus"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTextColor(Color.parseColor("#0F172A"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Mobilidade urbana leve e executiva"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(Color.parseColor("#475569"))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(20))
        }

        val statusCard = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = dp(4).toFloat()
            setCardBackgroundColor(Color.WHITE)
            setContentPadding(dp(20), dp(20), dp(20), dp(20))
        }

        val statusLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val statusTitle = TextView(this).apply {
            text = "Status do sistema"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
            gravity = Gravity.CENTER
        }

        val statusText = TextView(this).apply {
            text = "Aplicativo em execução e pronto para uso."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#334155"))
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(18))
        }

        val actionButton = MaterialButton(this).apply {
            text = "Abrir painel"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1D4ED8"))
            setOnClickListener {
                statusText.text = "Painel executivo ativo."
            }
        }

        statusLayout.addView(statusTitle)
        statusLayout.addView(statusText)
        statusLayout.addView(actionButton)
        statusCard.addView(statusLayout)

        val infoText = TextView(this).apply {
            text = "SPTrans • ThingSpeak • GPS • Android Studio"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#64748B"))
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(statusCard)
        root.addView(infoText)

        return root
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
