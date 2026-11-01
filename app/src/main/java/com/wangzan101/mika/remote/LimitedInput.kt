package com.wangzan101.mika.remote

/** Bounded stream read that also works on Android versions predating InputStream.readNBytes. */
fun java.io.InputStream.readLimited(maxBytes:Int):ByteArray {
    require(maxBytes>0)
    val output=java.io.ByteArrayOutputStream(minOf(maxBytes,8192))
    val buffer=ByteArray(8192)
    while(true){
        val count=read(buffer,0,minOf(buffer.size,maxBytes-output.size()+1))
        if(count<0)break
        if(count==0)continue
        require(output.size()+count<=maxBytes){"文件超过大小限制"}
        output.write(buffer,0,count)
    }
    return output.toByteArray()
}
