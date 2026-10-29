package com.jos.myapplication.ml

import java.util.Locale

data class ReplySuggestions(val replies:List<String>,val modelCount:Int) {
    val label:String get()=when {
        modelCount==3 -> "On-device ML suggestions · review before sending"
        modelCount>0 -> "On-device ML + local fallbacks · review before sending"
        else -> "Local fallback suggestions · review before sending"
    }
}

/** Output cleanup and authored fallbacks, not a second ML model or an external service. */
object ReplyQuality {
    const val MAX_LENGTH=140
    fun normalize(raw:String):String? {
        var text=raw.trim().replace('’','\'').replace(Regex("\\s+")," ")
        if(text.isBlank()||text.length>MAX_LENGTH||text.any{it.isISOControl()}||Regex("https?://|[{}\\[\\]]|<[^>]+>").containsMatchIn(text))return null
        text=text.removeSurrounding("\"").trim()
        text=when(text.lowercase(Locale.ROOT).trimEnd('.','!','?')) {
            "ok","okay","k" -> "Okay."
            "thx","ty","thanks" -> "Thanks!"
            "im good","i'm good" -> "I'm good, thanks!"
            else -> text
        }
        text=text.replace(Regex("\\bi\\b"),"I")
            .replace(Regex("\\b[Ii]m\\b"),"I'm").replace(Regex("\\b[Ii]ll\\b"),"I'll")
            .replace(Regex("\\bdont\\b",RegexOption.IGNORE_CASE),"don't")
            .replace(Regex("\\bcant\\b",RegexOption.IGNORE_CASE),"can't")
        if(text.isBlank()||Regex("(?i)^(i|we|they|the|a|an|is|are)$|\\b(and|or|to|for|of|with|the|a|an|at|in)$").containsMatchIn(text))return null
        text=text.replaceFirstChar{if(it.isLowerCase())it.titlecase(Locale.ROOT)else it.toString()}
        if(text.last().isLetterOrDigit())text+=if(Regex("^(what|when|where|who|why|how|could you|would you|can you|do you|are you)\\b",RegexOption.IGNORE_CASE).containsMatchIn(text))"?"else"."
        return text.takeIf{it.length<=MAX_LENGTH}
    }
    private fun key(text:String)=text.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9 ]"),"").trim()
    private fun family(text:String):String? {
        val value=key(text)
        return when {
            value in setOf("youre welcome","no problem","no worries","anytime","my pleasure")->"welcome"
            Regex("^(yes|sure|absolutely|of course|sounds good|okay|ok)( |$)").containsMatchIn(value)->"accept"
            Regex("^(no|nope|not this time|i cant|i cannot|i wont)( |$)").containsMatchIn(value)->"decline"
            Regex("^(hi|hello|hey|good morning|good evening)( there| friend)?$").matches(value)->"greeting"
            value in setOf("great","awesome","wonderful","fantastic","amazing")->"positive"
            Regex("^(maybe|not sure|im not sure)( |$)").containsMatchIn(value)->"uncertain"
            else->null
        }
    }
    fun distinct(a:String,b:String):Boolean {
        if(key(a)==key(b))return false
        if(family(a)!=null&&family(a)==family(b))return false
        val left=key(a).split(' ').filter{it.length>2}.toSet();val right=key(b).split(' ').filter{it.length>2}.toSet()
        return left.isEmpty()||right.isEmpty()||left.intersect(right).size.toDouble()/left.union(right).size<0.72
    }
    fun compose(current:String,preceding:List<String>,candidates:List<String>):ReplySuggestions {
        if(current.isBlank())return ReplySuggestions(emptyList(),0)
        val text=current.lowercase(Locale.ROOT)
        val emotional=Regex("\\b(sad|upset|lonely|anxious|stressed|depressed|hurt|crying|failed)\\b").containsMatchIn(text)
        val fallback=when {
            emotional->listOf("I'm sorry you're going through this.","Would you like to talk about it?","I'm here to listen.")
            Regex("^(hi|hello|hey|good morning|good evening)\\b").containsMatchIn(text)->listOf("Hi! How are you?","Hello! Good to hear from you.","Hey! What have you been up to?")
            Regex("\\b(thanks|thank you|thankyou|thx)\\b").containsMatchIn(text)->listOf("You're welcome!","Did that answer your question?","Let me know if you need anything else.")
            Regex("\\b(congratulations|passed|promoted|won|great news)\\b").containsMatchIn(text)->listOf("That's great news!","Congratulations!","I'd love to hear more.")
            Regex("\\b(lunch|dinner|meet|meeting|coffee|join|coming|free tomorrow)\\b").containsMatchIn(text)->listOf("Yes, that works for me.","I can't make it this time.",
                if(preceding.takeLast(6).any{Regex("\\b[0-9]{1,2}(?::[0-9]{2})?\\s?(?:am|pm)\\b",RegexOption.IGNORE_CASE).containsMatchIn(it)})"Does the time we discussed still work?"else"What time are you thinking?")
            Regex("\\b(please|assignment|deadline|project|report|submission)\\b").containsMatchIn(text)->listOf("Thanks for letting me know.","I'll check and get back to you.","Could you confirm the details?")
            '?' in text||Regex("^(what|when|where|why|how|who|can|could|would|do|did|are|is)\\b").containsMatchIn(text)->listOf("Let me check that.","Could you tell me a little more?","I'll get back to you shortly.")
            else->listOf("Thanks for the message.","Could you tell me a little more?","Let me get back to you.")
        }
        val replies=mutableListOf<String>();var modelCount=0
        fun add(raw:String,model:Boolean){val clean=normalize(raw)?:return;if(replies.size<3&&replies.all{distinct(it,clean)}){replies+=clean;if(model)modelCount++}}
        if(!emotional)candidates.take(10).forEach{add(it,true)}
        (fallback+listOf("Thanks for letting me know.","Could you share a little more detail?","I'll get back to you.","I understand.")).forEach{add(it,false)}
        check(replies.size==3)
        return ReplySuggestions(replies,modelCount)
    }
}
