package com.wangzan101.mika.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.remote.FleetController
import org.json.JSONObject
import org.json.JSONArray
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun FleetPlanEditor(controller:FleetController,devices:List<FleetController.Device>,original:JSONObject,onClose:()->Unit){
    val format=remember{DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")}
    var goal by remember{mutableStateOf(original.optString("goal"))}
    var first by remember{mutableStateOf(Instant.ofEpochMilli(maxOf(System.currentTimeMillis()+60000,original.optLong("nextAtMs"))).atZone(ZoneId.systemDefault()).format(format))}
    var mode by remember{mutableStateOf(if(!original.isNull("calendarTime") && original.has("calendarTime"))"calendar" else if(!original.isNull("intervalMs") && original.has("intervalMs"))"interval" else "once")}
    var interval by remember{mutableStateOf((original.optDouble("intervalMs",3600000.0)/3600000).toString())}
    var clock by remember{mutableStateOf(original.optString("calendarTime","09:00").takeUnless{it=="null"} ?: "09:00")}
    var zone by remember{mutableStateOf(original.optString("calendarZone",ZoneId.systemDefault().id).takeUnless{it=="null"} ?: ZoneId.systemDefault().id)}
    var days by remember{mutableStateOf(original.optJSONArray("calendarDays")?.let{array->(0 until array.length()).map{array.getInt(it)}.toSet()} ?: emptySet<Int>())}
    var condition by remember{mutableStateOf(original.optString("conditionGoal").takeUnless{it=="null"} ?: "")}
    var reminder by remember{mutableStateOf(original.optString("reminderText").takeUnless{it=="null"} ?: "")}
    var idle by remember{mutableStateOf(original.optBoolean("idleOnly"))}
    var enabled by remember{mutableStateOf(original.optBoolean("enabled",true))}
    var busy by remember{mutableStateOf(false)}
    var results by remember{mutableStateOf(mapOf<String,String>())}
    val scope=rememberCoroutineScope()
    val planId=remember{original.optString("id").ifBlank{java.util.UUID.randomUUID().toString()}}
    var pending by remember{mutableStateOf(mapOf<String,String>())}
    fun save(payload:String,targets:List<FleetController.Device>){
        busy=true
        scope.launch{try{for(device in targets){try{
            controller.request(device,"/plans",JSONObject(payload))
            pending=pending-device.id;results=results+(device.name to "已保存")
        }catch(e:Exception){pending=pending+(device.id to payload);results=results+(device.name to (e.message ?: "失败"))}}}finally{busy=false}
        if(pending.isEmpty())onClose()}
    }

    AlertDialog(onDismissRequest={if(!busy)onClose()},title={Text("定时任务")},text={Column(Modifier.heightIn(max=550.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedTextField(value=goal,onValueChange={goal=it},label={Text("任务内容")},enabled=!busy)
        Row{Checkbox(enabled=!busy,checked=enabled,onCheckedChange={enabled=it});Text("启用",Modifier.padding(top=12.dp))}
        Row{Checkbox(enabled=!busy,checked=idle,onCheckedChange={idle=it});Text("仅设备空闲时执行",Modifier.padding(top=12.dp))}
        Row{listOf("once" to "一次","interval" to "间隔","calendar" to "每周").forEach{(key,label)->FilterChip(enabled=!busy,selected=mode==key,onClick={mode=key},label={Text(label)})}}
        if(mode!="calendar")OutlinedTextField(value=first,onValueChange={first=it},label={Text("首次执行 yyyy-MM-dd HH:mm")},enabled=!busy)
        if(mode=="interval")OutlinedTextField(value=interval,onValueChange={interval=it},label={Text("间隔小时")},enabled=!busy)
        if(mode=="calendar"){
            OutlinedTextField(value=clock,onValueChange={clock=it},label={Text("时间 HH:mm")},enabled=!busy)
            OutlinedTextField(value=zone,onValueChange={zone=it},label={Text("时区")},enabled=!busy)
            (1..7).toList().chunked(3).forEach{row->Row{row.forEach{day->FilterChip(selected=day in days,onClick={days=if(day in days)days-day else days+day},enabled=!busy,label={Text(listOf("一","二","三","四","五","六","日")[day-1])})}}}
            Text("未选择星期时每天执行。",style=MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(value=condition,onValueChange={condition=it},label={Text("执行前检查条件，可留空")},enabled=!busy)
        OutlinedTextField(value=reminder,onValueChange={reminder=it},label={Text("固定提醒原文，可留空")},enabled=!busy)
        Text("填写固定提醒时，到点直接提醒，不调用模型执行任务。",style=MaterialTheme.typography.bodySmall)
        if(pending.isNotEmpty())TextButton(enabled=!busy,onClick={val payload=pending.values.first();save(payload,devices.filter{it.id in pending})}){Text("重试失败设备（${pending.size}）")}
        results.forEach{(name,result)->Text("$name：$result")}
    }},confirmButton={TextButton(enabled=!busy && goal.isNotBlank(),onClick={
        try{
            val plan=JSONObject(original.toString());val id=planId;plan.put("id",id).put("goal",goal).put("idleOnly",idle).put("enabled",enabled)
            listOf("intervalMs","calendarTime","calendarZone","calendarDays","conditionGoal","reminderText").forEach{plan.remove(it)}
            plan.put("nextAtMs",if(mode=="calendar")System.currentTimeMillis()+60000 else LocalDateTime.parse(first,format).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            if(mode=="interval"){val hours=interval.toDouble();require(hours>0 && hours<=8760);plan.put("intervalMs",(hours*3600000).toLong())}
            if(mode=="calendar")plan.put("calendarTime",clock).put("calendarZone",zone).put("calendarDays",JSONArray(days.sorted()))
            if(condition.isNotBlank())plan.put("conditionGoal",condition)
            if(reminder.isNotBlank())plan.put("reminderText",reminder)
            pending=emptyMap()
            save(JSONObject().put("action","save").put("id",id).put("plan",plan).toString(),devices)
        }catch(e:Exception){results=mapOf("设置" to (e.message ?: "请检查时间与间隔"))}
    }){Text("保存")}},dismissButton={TextButton(enabled=!busy,onClick=onClose){Text("关闭")}})
}
