import Foundation
import AppKit
import VideoToolbox
let dest=CommandLine.arguments[1]
let label=CommandLine.arguments[2]
var output=Data()
func packet(_ d:Data){var n=UInt32(d.count).bigEndian;output.append(Data(bytes:&n,count:4));output.append(d)}
var encoder:VTCompressionSession?
let callback:VTCompressionOutputCallback = {_,_,status,_,sample in
 guard status==0,let sample,let format=CMSampleBufferGetFormatDescription(sample),let block=CMSampleBufferGetDataBuffer(sample) else{return}
 for index in 0..<2 {var pointer:UnsafePointer<UInt8>?;var count=0;var total=0
 if CMVideoFormatDescriptionGetH264ParameterSetAtIndex(format,parameterSetIndex:index,parameterSetPointerOut:&pointer,parameterSetSizeOut:&count,parameterSetCountOut:&total,nalUnitHeaderLengthOut:nil)==0,let pointer{var d=Data([0,0,0,1]);d.append(pointer,count:count);packet(d)}}
 let size=CMBlockBufferGetDataLength(block);var data=Data(count:size)
 _=data.withUnsafeMutableBytes{CMBlockBufferCopyDataBytes(block,atOffset:0,dataLength:size,destination:$0.baseAddress!)}
 var offset=0;var annex=Data()
 while offset+4<=size{let n=data[offset..<offset+4].reduce(0){($0<<8)|Int($1)};offset+=4;guard offset+n<=size else{break};annex.append(contentsOf:[0,0,0,1]);annex.append(data[offset..<offset+n]);offset+=n};packet(annex)
}
assert(VTCompressionSessionCreate(allocator:nil,width:360,height:780,codecType:kCMVideoCodecType_H264,encoderSpecification:nil,imageBufferAttributes:nil,compressedDataAllocator:nil,outputCallback:callback,refcon:nil,compressionSessionOut:&encoder)==0)
let session=encoder!
VTSessionSetProperty(session,key:kVTCompressionPropertyKey_RealTime,value:kCFBooleanTrue)
VTSessionSetProperty(session,key:kVTCompressionPropertyKey_AllowFrameReordering,value:kCFBooleanFalse)
VTSessionSetProperty(session,key:kVTCompressionPropertyKey_MaxKeyFrameInterval,value:10 as CFNumber)
for frame in 0..<20 {
 var pixel:CVPixelBuffer?;CVPixelBufferCreate(nil,360,780,kCVPixelFormatType_32ARGB,nil,&pixel);let p=pixel!
 CVPixelBufferLockBaseAddress(p,[])
 let context=CGContext(data:CVPixelBufferGetBaseAddress(p),width:360,height:780,bitsPerComponent:8,bytesPerRow:CVPixelBufferGetBytesPerRow(p),space:CGColorSpaceCreateDeviceRGB(),bitmapInfo:CGImageAlphaInfo.noneSkipFirst.rawValue)!
 context.setFillColor(NSColor(calibratedRed:label=="A" ? 0.08:0.22,green:0.15,blue:0.3,alpha:1).cgColor);context.fill(CGRect(x:0,y:0,width:360,height:780))
 NSGraphicsContext.saveGraphicsState();NSGraphicsContext.current=NSGraphicsContext(cgContext:context,flipped:false)
 let attrs:[NSAttributedString.Key:Any]=[.font:NSFont.systemFont(ofSize:27),.foregroundColor:NSColor.white]
 ("设备 \(label)" as NSString).draw(at:NSPoint(x:30,y:650),withAttributes:attrs)
 ("" as NSString).draw(at:NSPoint(x:30,y:520),withAttributes:[.font:NSFont.systemFont(ofSize:20),.foregroundColor:NSColor.white])
 NSGraphicsContext.restoreGraphicsState();context.setFillColor(NSColor.systemMint.cgColor);context.fillEllipse(in:CGRect(x:30+frame*12,y:250,width:45,height:45));CVPixelBufferUnlockBaseAddress(p,[])
 VTCompressionSessionEncodeFrame(session,imageBuffer:p,presentationTimeStamp:CMTime(value:Int64(frame),timescale:10),duration:CMTime(value:1,timescale:10),frameProperties:nil,sourceFrameRefcon:nil,infoFlagsOut:nil)
}
VTCompressionSessionCompleteFrames(session,untilPresentationTimeStamp:.invalid);VTCompressionSessionInvalidate(session)
try output.write(to:URL(fileURLWithPath:dest));print("Generated",label,output.count)
