import Foundation
import Combine
struct NativeChatExchange:Codable {var goal:String}
struct AgentSnapshot {var commandId:String?;var phase="idle";var title="idle";var detail="";var exchanges:[NativeChatExchange]?;var busy=false}
struct ChatMessage {var id:String;var status:String}
@MainActor final class FakeClient {
 @Published var snapshot=AgentSnapshot()
 var connected=true;var sending=false;var switchingChat=false;var chatMessages:[ChatMessage]=[];var calls:[String]=[]
 func command(_ action:String,text:String,commandId:String) async {calls.append(commandId);try? await Task.sleep(for:.milliseconds(20));chatMessages.append(.init(id:commandId+"-user",status:"已接收"));snapshot=AgentSnapshot(commandId:commandId,phase:"completed",title:"任务完成",detail:text,exchanges:[.init(goal:text)])}
}
@MainActor final class NearbyAgent {let id:String;let name:String;var approved=true;var demo=false;let client=FakeClient();init(_ id:String){self.id=id;name=id}}
@main struct Test {
 @MainActor static func main() async {
  UserDefaults.standard.removeObject(forKey:"fleetChats")
  let a=NearbyAgent("A"),b=NearbyAgent("B"),c=NearbyAgent("offline"),d=NearbyAgent("busy");c.client.connected=false;d.client.snapshot.busy=true
  let store=FleetChatStore();store.observe([a,b,c,d]);await store.send("test",to:[a,b,c,d]);try? await Task.sleep(for:.milliseconds(100))
  assert(store.batches.count==1);let results=store.batches[0].deliveries;assert(results.count==3);assert(a.client.calls.count==1 && b.client.calls.count==1);assert(a.client.calls[0] != b.client.calls[0]);assert(c.client.calls.isEmpty && d.client.calls.isEmpty);assert(results.first{$0.deviceID=="A"}?.reply=="test");assert(results.first{$0.deviceID=="B"}?.phase=="completed");assert(results.first{$0.deviceID=="busy"}?.status=="设备忙，未发送");assert(FleetChatStore().batches.count==1)
  UserDefaults.standard.removeObject(forKey:"fleetChats");print("PASS: concurrent dispatch, unique IDs, offline exclusion, busy isolation, reply correlation, persistence")
 }
}
