package br.com.autonomiccs.cloudTraces.beans;

import java.util.Comparator;

public class VirtualMachineComparator implements Comparator<VirtualMachine> {
	@Override
	public int compare(VirtualMachine vm1, VirtualMachine vm2) {
	    if (vm1.getVmServiceOffering().getMemoryInMegaByte() > vm2.getVmServiceOffering().getMemoryInMegaByte()) {
	        return -1;
	    } else if (vm1.getVmServiceOffering().getMemoryInMegaByte() < vm2.getVmServiceOffering().getMemoryInMegaByte()) {
	        return 1;
	    }
	    return 0;
	}
}
