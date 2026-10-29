package com.jos.myapplication.ml

data class StudyAlertDecision(val category:String?,val score:Double?,val alert:Boolean)
/** Demonstrable opt-in local ML: incoming study-related chat -> study notification, not urgency detection. */
object StudyAlerts {
    const val COOLDOWN_MS=30000L
    fun decide(text:String,enabled:Boolean,now:Long,lastAlert:Long,model:MessageCategories=MessageCategories()):StudyAlertDecision {
        if(!enabled)return StudyAlertDecision(null,null,false)
        val prediction=model.predict(text)?:return StudyAlertDecision(null,null,false)
        val alert=prediction.category=="Study"&&prediction.score>=0.80&&prediction.matchedWords>=2&&now-lastAlert>=COOLDOWN_MS
        return StudyAlertDecision(prediction.category,prediction.score,alert)
    }
}
