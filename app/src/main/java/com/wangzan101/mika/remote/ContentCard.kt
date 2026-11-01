package com.wangzan101.mika.remote
import android.graphics.*
import android.text.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/** Render controller-written graphic content locally; each device receives its own PNG. */
object ContentCard {
    fun render(card:JSONObject):ByteArray {
        val title=card.getString("title");val body=card.getString("body")
        require(title.length in 1..60 && body.length in 1..500){"图文卡片内容过长，请减少单张文字"}
        val accent=runCatching{Color.parseColor(card.optString("accent","#287A76"))}.getOrDefault(Color.rgb(40,122,118))
        val bitmap=Bitmap.createBitmap(1080,1440,Bitmap.Config.ARGB_8888)
        try{
            val canvas=Canvas(bitmap);canvas.drawColor(Color.rgb(247,247,242))
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=accent};canvas.drawRoundRect(64f,72f,1016f,92f,10f,10f,paint)
            fun text(value:String,size:Float,y:Float,bold:Boolean):Int{
                val pen=TextPaint(Paint.ANTI_ALIAS_FLAG).apply{color=if(bold)accent else Color.rgb(35,39,43);textSize=size;typeface=if(bold)Typeface.DEFAULT_BOLD else Typeface.DEFAULT}
                val layout=StaticLayout.Builder.obtain(value,0,value.length,pen,920).setLineSpacing(14f,1f).setIncludePad(false).build()
                require(y+layout.height<1360){"图文卡片排版超出范围，请缩短内容"}
                canvas.save();canvas.translate(80f,y);layout.draw(canvas);canvas.restore();return layout.height
            }
            val height=text(title,64f,150f,true);text(body,40f,210f+height,false)
            return ByteArrayOutputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}
        }finally{bitmap.recycle()}
    }
}
