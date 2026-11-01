import SwiftUI

struct NearbyRadar: View {
    @ObservedObject var hub: NearbyHub
    @State private var selected: NearbyAgent?
    @State private var page=0
    @State private var renameDevice:NearbyAgent?
    @State private var deviceName=""
    @State private var showRename=false
    @State private var showGroup=false
    @State private var groupName=""
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    private var visible:[NearbyAgent]{Array(hub.agents.dropFirst(page*6).prefix(6))}
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment:.leading,spacing:18){
                    HStack{Text("附近").font(.caption.monospaced().weight(.medium)).tracking(3).foregroundStyle(.mint);Spacer();Circle().fill(.mint).frame(width:6,height:6);Text("局域网").font(.caption).foregroundStyle(.secondary)}
                    Text(hub.discoveryStatus).font(.subheadline).foregroundStyle(.secondary)
                    GeometryReader{geo in
                        let w=geo.size.width;let center=CGPoint(x:w/2,y:215)
                        ZStack {
                            ForEach([100.0,190.0,280.0,350.0],id:\.self){size in Circle().stroke(Color.primary.opacity(0.055),lineWidth:1).frame(width:size,height:size).position(center)}
                            if !reduceMotion {
                                TimelineView(.animation(minimumInterval:1/30,paused:scenePhase != .active)){timeline in
                                    let phase=timeline.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy:7)/7
                                    RippleShape(progress:phase).stroke(AngularGradient(colors:[.mint,.cyan,.blue,.purple,.pink,.orange,.mint],center:.center),lineWidth:phase<0.1 ? 0:2).blur(radius:2).opacity(sin(phase * .pi)*0.65)
                                        .frame(width:w,height:w).position(center).allowsHitTesting(false)
                                }
                            }
                            ForEach(Array(visible.enumerated()),id:\.element.id){index,agent in
                                let angle = -Double.pi/2 + Double(index)*2*Double.pi/Double(max(visible.count,3))
                                let point=CGPoint(x:center.x+cos(angle)*min(w*0.36,143),y:center.y+sin(angle)*154)
                                Button(action: { selected=agent }) { DeviceOrb(agent:agent,active:scenePhase == .active) }.buttonStyle(.plain).contextMenu{Button("修改设备名称",systemImage:"pencil"){renameDevice=agent;deviceName=agent.name;showRename=true};Button("设置分组",systemImage:"folder"){renameDevice=agent;groupName=hub.deviceGroups[agent.id] ?? "";showGroup=true}}.position(point)
                            }
                            VStack(spacing:4){
                                ZStack {
                                    Circle().fill(.mint.opacity(0.035)).frame(width:100,height:100)
                                    Circle().stroke(.mint.opacity(0.18),lineWidth:1).frame(width:100,height:100)
                                    AoraAnimatedAvatar(phase:"idle",active:scenePhase == .active).frame(width:90,height:90)
                                }
                                Text("本机").font(.subheadline.weight(.semibold))
                            }.position(x:center.x,y:center.y+10)
                            if hub.agents.isEmpty {
                                VStack(spacing:7){Image(systemName:"wifi").font(.title2).foregroundStyle(.mint.opacity(0.6));Text("暂无附近 agent").font(.caption).foregroundStyle(.secondary)}.position(x:center.x,y:45)
                            }
                        }
                    }.frame(height:425)
                    if hub.agents.count>6 {HStack{Button("上一组"){page=max(0,page-1)}.disabled(page==0);Spacer();Text("\(page+1) / \((hub.agents.count+5)/6)").font(.caption);Spacer();Button("下一组"){page+=1}.disabled((page+1)*6>=hub.agents.count)}.font(.caption)}
                    HStack{Label("\(hub.agents.filter{$0.approved && $0.available && $0.client.connected}.count) 已连接",systemImage:"link");Spacer();Text("\(hub.agents.filter{!$0.demo}.count) 附近设备")}.font(.caption).foregroundStyle(.secondary)
                }.padding(.horizontal,22).padding(.vertical,12)
            }.background(Color(uiColor:.systemGroupedBackground))
                .navigationTitle("agent").navigationBarTitleDisplayMode(.inline)
                .alert("修改设备名称",isPresented:$showRename){TextField("设备名称",text:$deviceName);Button("取消",role:.cancel){};Button("保存"){if let device=renameDevice{hub.rename(device,to:deviceName)}}.disabled(deviceName.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty)}
                .alert("设备分组",isPresented:$showGroup){TextField("分组名称，留空取消分组",text:$groupName);Button("取消",role:.cancel){};Button("保存"){if let device=renameDevice{hub.setGroup(device.id,name:groupName)}}}
                .sheet(item:$selected){agent in AgentPanel(agent:agent,hub:hub)}
                .onChange(of: hub.requestedDevice) { _, id in if let id { selected = hub.agents.first { $0.id == id }; hub.requestedDevice = nil } }
        }
    }
}
struct DeviceOrb:View {
    @ObservedObject var agent:NearbyAgent
    var active:Bool
    var body:some View {
        VStack(spacing:4){
            ZStack(alignment:.bottomTrailing){
                Circle().stroke(agent.approved ? Color.mint.opacity(0.6):Color.primary.opacity(0.12),lineWidth:1).frame(width:67,height:67)
                AoraAnimatedAvatar(phase:agent.phase,active:active && agent.available,shape:agent.avatarShape,hue:agent.avatarHue).frame(width:67,height:67)
                Circle().fill(agent.demo ? Color.purple:agent.available && agent.client.connected ? Color.mint:agent.available ? Color.orange:Color.gray).frame(width:9,height:9).overlay(Circle().stroke(Color.black,lineWidth:2)).offset(x:-5,y:-4)
            }
            Text(agent.name).font(.caption.weight(.medium)).lineLimit(1).frame(width:104)
            Text(agent.demo ? "演示 · "+agent.client.snapshot.title:agent.connection).font(.system(size:10)).foregroundStyle(.secondary).lineLimit(1).frame(width:104)
        }.accessibilityElement(children:.combine)
    }
}
struct AgentPanel:View {
    @ObservedObject var agent:NearbyAgent
    @ObservedObject var hub:NearbyHub
    @Environment(\.scenePhase) private var scenePhase
    var body:some View {
        if agent.approved || agent.demo {
            DeviceWorkspace(hub:hub,initialDevice:agent.id,singleDevice:true)
                .onAppear{hub.select(agent)}
        } else {
            NavigationStack {
                VStack(spacing:22){
                    Spacer()
                    AoraAnimatedAvatar(phase:agent.phase,active:scenePhase == .active,shape:agent.avatarShape,hue:agent.avatarHue).frame(width:190,height:190)
                    Text(agent.name).font(.title.bold())
                    Text(agent.connection).foregroundStyle(.secondary)
                    Text("请在安卓端允许连接").multilineTextAlignment(.center).foregroundStyle(.secondary)
                    Button(agent.connection == "等待安卓允许" ? "等待安卓允许…":"申请连接",systemImage:"link") {Task{await hub.invite(agent)}}.buttonStyle(.borderedProminent).tint(.mint).disabled(agent.connection == "等待安卓允许" || !agent.available)
                    Spacer()
                }.padding(30).navigationTitle("连接 agent").navigationBarTitleDisplayMode(.inline)
            }
        }
    }
}
struct RippleShape:Shape {
    var progress:Double
    func path(in rect:CGRect)->Path {
        var p=Path();let radius=rect.width*0.68*progress
        for i in 0...180{let angle=Double(i)/180*2*Double.pi;let r=radius+(sin(angle*5+progress*9)+sin(angle*3-progress*7))*4*progress;let pt=CGPoint(x:rect.midX+cos(angle)*r,y:rect.midY+sin(angle)*r);if i==0{p.move(to:pt)}else{p.addLine(to:pt)}}
        p.closeSubpath();return p
    }
}
