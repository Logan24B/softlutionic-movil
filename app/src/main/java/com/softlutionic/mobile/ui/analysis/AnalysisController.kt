package com.softlutionic.mobile.ui.analysis

import com.softlutionic.mobile.R
import com.softlutionic.mobile.databinding.ViewAnalysisBinding
import com.softlutionic.mobile.ui.invoices.InvoiceViews

/** Local information only. Future analytics must use HTTPS/API, never direct DW access. */
class AnalysisController(view: ViewAnalysisBinding,back: ()->Unit) {
    init {
        val ui=InvoiceViews(view.root.context)
        val titles=view.root.resources.getStringArray(R.array.analysis_indicator_titles)
        val descriptions=view.root.resources.getStringArray(R.array.analysis_indicator_descriptions)
        titles.zip(descriptions).forEach { (title,description) ->
            val body=ui.column().apply { addView(ui.text(title,20f)); addView(ui.text(description,14f,true)) }
            view.indicators.addView(ui.card().apply { addView(body) })
        }
        view.back.setOnClickListener { back() }
    }
}
