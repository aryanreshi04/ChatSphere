package com.jos.myapplication.location
import com.jos.myapplication.data.MessagePolicy
object DirectionsPolicy {
    val modes=setOf("walking","driving","bicycling","transit")
    fun url(latitude:Double,longitude:Double,origin:Pair<Double,Double>?=null,mode:String="walking"):String {
        require(MessagePolicy.validLocation(latitude,longitude)&&mode in modes)
        origin?.let{require(MessagePolicy.validLocation(it.first,it.second))}
        val start=origin?.let{"&origin="+it.first+","+it.second}.orEmpty()
        return "https://www.google.com/maps/dir/?api=1&destination="+latitude+","+longitude+start+"&travelmode="+mode+"&dir_action=navigate"
    }
    fun validPlace(name:String,latitude:Double,longitude:Double)=name.isNotBlank()&&name.length<=80&&name.none{it.isISOControl()}&&MessagePolicy.validLocation(latitude,longitude)
}
