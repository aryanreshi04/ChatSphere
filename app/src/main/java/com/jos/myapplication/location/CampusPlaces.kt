package com.jos.myapplication.location
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** No campus is bundled: only explicitly supplied/confirmed coordinates become destinations. */
class CampusPlaces(context:Context,prefsName:String="chatsphere_campus") {
    private val prefs=context.getSharedPreferences(prefsName,0)
    fun load(asset:JSONObject):JSONObject {
        val saved=prefs.getString("configuration",null)?:return validated(asset)
        return runCatching{validated(JSONObject(saved))}.getOrElse{validated(asset)}
    }
    private fun validated(source:JSONObject):JSONObject {
        val result=JSONObject(source.toString());val safe=JSONArray();val entries=source.optJSONArray("destinations")?:JSONArray()
        for(i in 0 until minOf(entries.length(),50)){
            val entry=entries.optJSONObject(i)?:continue
            if(DirectionsPolicy.validPlace(entry.optString("name"),entry.optDouble("latitude"),entry.optDouble("longitude"))){
                val copy=JSONObject(entry.toString())
                if(copy.optString("id").isBlank())copy.put("id",UUID.nameUUIDFromBytes((copy.optString("name")+copy.optDouble("latitude")+copy.optDouble("longitude")).toByteArray()).toString())
                safe.put(copy)
            }
        }
        return result.put("destinations",safe)
    }
    fun add(current:JSONObject,campus:String,name:String,latitude:Double,longitude:Double,editingId:String?=null):JSONObject {
        require(campus.isNotBlank()&&campus.length<=80&&campus.none{it.isISOControl()}){"Enter your campus/place-group name (up to 80 characters)"}
        require(DirectionsPolicy.validPlace(name,latitude,longitude)){"Enter a name and valid coordinates"}
        val result=validated(current);val places=result.getJSONArray("destinations")
        val index=if(editingId==null)-1 else (0 until places.length()).firstOrNull{places.getJSONObject(it).optString("id")==editingId}?:error("Destination no longer exists")
        require(index>=0||places.length()<50){"This configuration supports up to 50 destinations"}
        require((0 until places.length()).none{it!=index&&places.getJSONObject(it).getString("name").equals(name.trim(),true)}){"A destination already has that name"}
        val entry=JSONObject().put("id",editingId?:UUID.randomUUID().toString()).put("name",name.trim()).put("latitude",latitude).put("longitude",longitude).put("verified",true)
        if(index>=0)places.put(index,entry)else places.put(entry)
        result.put("name",campus.trim())
        check(prefs.edit().putString("configuration",result.toString()).commit()){"Could not save destinations"}
        return result
    }
}
