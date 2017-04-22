package br.com.autonomiccs.cloudTraces.beans;

import java.util.Comparator;

public class HostComparator implements Comparator<Host> {
	@Override
	public int compare(Host host1, Host host2) {
	    if (host1.getMemoryUsedInMib() < host2.getMemoryUsedInMib() ) {
	        return -1;
	    } else if (host1.getMemoryUsedInMib()  > host2.getMemoryUsedInMib() ) {
	        return 1;
	    }
	    return 0;
	}
}