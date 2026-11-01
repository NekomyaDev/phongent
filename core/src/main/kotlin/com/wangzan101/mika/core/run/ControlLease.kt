package com.wangzan101.mika.core.run

/** A disconnected controller loses permission, but expiry never resumes the executor. */
class ControlLease(private val clock:()->Long,private val ttl:Long=20000){
    private data class Lease(val owner:String,var until:Long)
    private val leases=mutableMapOf<String,Lease>()
    private fun active(run:String):Lease? { leases.entries.removeAll{it.value.until<=clock()};return leases[run] }
    @Synchronized fun claim(run:String,owner:String,takeover:Boolean=false):Boolean {
        require(run.isNotBlank() && owner.isNotBlank())
        val prior=active(run)
        if(prior!=null && prior.owner!=owner && !takeover)return false
        leases[run]=Lease(owner,clock()+ttl);return true
    }
    @Synchronized fun owns(run:String,owner:String,renew:Boolean=false):Boolean {
        val lease=active(run) ?: return false
        if(lease.owner!=owner)return false
        if(renew)lease.until=clock()+ttl
        return true
    }
    @Synchronized fun occupiedByOther(run:String,owner:String)=active(run)?.owner?.let{it!=owner} ?: false
    @Synchronized fun release(run:String,owner:String):Boolean {
        if(!owns(run,owner))return false
        leases.remove(run);return true
    }
}
