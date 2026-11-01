package com.wangzan101.mika.core.run
import org.junit.Assert.*
import org.junit.Test
class ControlLeaseTest {
    @Test fun onlyOwnerCanInputOrRelease(){var now=0L;val l=ControlLease({now});assertTrue(l.claim("run","a"));assertFalse(l.claim("run","b"));assertFalse(l.owns("run","b"));assertFalse(l.release("run","b"));assertTrue(l.owns("run","a"))}
    @Test fun explicitTakeoverRevokesPreviousOwner(){val l=ControlLease({0});l.claim("run","a");assertTrue(l.claim("run","b",true));assertFalse(l.owns("run","a"));assertTrue(l.owns("run","b"))}
    @Test fun ownerHeartbeatRenewsButObserverDoesNot(){var now=0L;val l=ControlLease({now},10);l.claim("run","a");now=5;assertTrue(l.owns("run","a",true));now=10;assertFalse(l.owns("run","b",true));assertTrue(l.owns("run","a"));now=15;assertFalse(l.owns("run","a"));assertTrue(l.claim("run","b"))}
    @Test fun runIdentitySeparatesControl(){val l=ControlLease({0});l.claim("one","a");l.claim("two","b");assertFalse(l.owns("two","a"));assertTrue(l.release("one","a"));assertTrue(l.owns("two","b"))}
}
