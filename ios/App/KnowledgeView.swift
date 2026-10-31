import SwiftUI

struct KnowledgeItem:Decodable,Identifiable {
    var id:String;var type:String;var title:String;var text:String;var status:String
}
struct KnowledgeView:View {
    @ObservedObject var hub:NearbyHub
    var defaultDevice:String
    @State private var target=""
    @State private var type="memory"
    @State private var kind="fact"
    @State private var title=""
    @State private var description=""
    @State private var memoryText=""
    @State private var skillText="## 入口\n\n## 步骤\n\n## 容易出错的地方\n\n## 怎样算办成\n\n## 什么时候停下交给用户\n"
    @State private var requestId=UUID().uuidString
    @State private var lastPayload=""
    @State private var results:[String:String]=[:]
    @State private var items:[String:[KnowledgeItem]]=[:]
    @State private var notices:[String:String]=[:]
    @State private var saving=false
    @FocusState private var editing:Bool
    private var connected:[NearbyAgent] {hub.agents.filter{$0.approved && $0.available && $0.client.connected && !$0.demo}}
    private var destinations:[NearbyAgent] {connected.filter{target=="all" || $0.id==target}}
    var body:some View {
        Form {
            Section("添加到") {
                Picker("设备",selection:$target) {
                    if !connected.contains(where:{$0.id==target}) && target != "all" {Text("当前设备未连接").tag(target)}
                    ForEach(connected){Text($0.name).tag($0.id)}
                    Text("所有已连接设备（\(connected.count)）").tag("all")
                }.disabled(saving)
            }
            Section("新增") {
                Picker("类型",selection:$type){Text("记忆").tag("memory");Text("技能").tag("skill")}.pickerStyle(.segmented)
                if type=="memory" {
                    Picker("记忆类型",selection:$kind){Text("事实").tag("fact");Text("偏好").tag("preference");Text("做法").tag("procedure")}
                    TextField("希望 agent 记住的内容",text:$memoryText,axis:.vertical).lineLimit(4...12).focused($editing)
                    if kind=="procedure" {Text("做法会进入安卓的待确认列表。").font(.caption).foregroundStyle(.secondary)}
                } else {
                    TextField("技能名称",text:$title).focused($editing)
                    TextField("适用场景",text:$description,axis:.vertical).focused($editing)
                    TextEditor(text:$skillText).frame(minHeight:220).focused($editing)
                    Text("按五个章节填写步骤。保存后加入安卓个人技能，状态为待验证。").font(.caption).foregroundStyle(.secondary)
                }
                Button(saving ? "正在保存…":"保存到所选设备") {Task{await save()}}
                    .disabled(saving || destinations.isEmpty || (type=="memory" ? memoryText.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty : title.isEmpty || description.isEmpty || skillText.isEmpty))
            }.disabled(saving)
            if !results.isEmpty {Section("保存结果") {ForEach(results.keys.sorted(),id:\.self){key in Text(results[key] ?? "")}}}
            ForEach(destinations) {agent in
                Section(agent.name) {
                    if let notice=notices[agent.id] {Text(notice).font(.caption).foregroundStyle(.orange)}
                    ForEach((items[agent.id] ?? []).filter{$0.type==type}) {item in
                        NavigationLink {
                            ScrollView {Text(item.text).textSelection(.enabled).frame(maxWidth:.infinity,alignment:.leading).padding()}.navigationTitle(item.type=="memory" ? "记忆":"技能")
                        } label:{VStack(alignment:.leading,spacing:5){Text(item.type=="memory" ? item.text:item.title).lineLimit(3);Text(status(item.status)).font(.caption).foregroundStyle(.secondary)}}
                    }
                    if items[agent.id]?.isEmpty == true {Text("暂无记录").foregroundStyle(.secondary)}
                }
            }
        }.navigationTitle("记忆与技能").navigationBarTitleDisplayMode(.inline).toolbar(.visible,for:.navigationBar)
        .scrollDismissesKeyboard(.interactively)
        .toolbar {ToolbarItemGroup(placement:.keyboard){Spacer();Button("完成"){editing=false}}}
        .onAppear {if target.isEmpty{target=defaultDevice}}
        .task(id:target) {await reload()}
        .refreshable {await reload()}
    }
    private func status(_ raw:String)->String { ["active":"已启用","pending":"待安卓确认","trial":"已保存 · 待验证","suspended":"已停用"][raw] ?? raw }
    private func reload() async {
        for agent in destinations {
            guard agent.client.snapshot.knowledgeVersion == 1 else{notices[agent.id]="请先更新安卓安装包";continue}
            do {
                struct Response:Decodable {var items:[KnowledgeItem];var memoryEnabled:Bool}
                let response=try JSONDecoder().decode(Response.self,from:await agent.client.request("/knowledge"))
                items[agent.id]=response.items;notices[agent.id]=response.memoryEnabled ? nil:"安卓当前关闭了记忆使用；保存不会自动改变此开关。"
            } catch {notices[agent.id]="读取失败："+error.localizedDescription}
        }
    }
    private func save() async {
        editing=false;saving=true;defer{saving=false}
        let content=type=="memory" ? memoryText:skillText
        let signature=[type,kind,title,description,content].joined(separator:"\u{0}")
        if signature != lastPayload {requestId=UUID().uuidString;lastPayload=signature;results=[:]}
        let captured=destinations
        let body=["id":requestId,"type":type,"kind":kind,"title":title,"description":description,"text":content]
        guard let data=try? JSONEncoder().encode(body) else{return}
        for agent in captured {
            guard agent.client.snapshot.knowledgeVersion == 1 else {results[agent.id]=agent.name+"：未保存，请更新安卓";continue}
            do {
                let response=try await agent.client.request("/knowledge",body:data)
                let json=try JSONSerialization.jsonObject(with:response) as? [String:Any]
                results[agent.id]=agent.name+"："+status(json?["status"] as? String ?? "已保存")
            } catch {results[agent.id]=agent.name+"："+error.localizedDescription+"；可在本页重试，将沿用同一请求编号"}
        }
        await reload()
    }
}
