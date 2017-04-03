package br.com.autonomiccs.cloudTraces.algorithms.management;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import br.com.autonomiccs.cloudTraces.beans.Host;
import br.com.autonomiccs.cloudTraces.beans.HostComparator;
import br.com.autonomiccs.cloudTraces.beans.VirtualMachine;
import br.com.autonomiccs.cloudTraces.beans.VirtualMachineComparator;
import br.com.autonomiccs.cloudTraces.exceptions.GoogleTracesToCloudTracesException;

public class ClusterVMsBalancingByRAMusage extends ClusterAdministrationAlgorithmEmptyImpl {
	
	protected long clusterRAMusageAverage = 0;
	
    @Override
    public List<Host> rankHosts(List<Host> hosts) {
        return sortHostsByRAMusageDecreasing(hosts);
    }

	/**
	 * Balance the cluster based on the RAM usage of each host in it.
	 * @param hostsAtivos list of active hosts in the cluster.
	 * @param clusterRAMusageAverage RAM average used by the hosts.
	 */
    @Override
    public Map<VirtualMachine, Host> mapVMsToHost(List<Host> rankedHosts) {
    	Map<VirtualMachine, Host> mapOfMigrations = new HashMap<>();
		List<Host> possibleTargets = new ArrayList<>();
		List<Host> hostsToBeBalanced = new ArrayList<>();
		calculateClusterRAMaverageUsage(rankedHosts);
		
		//Verifica os hosts que precisam ser balanceados e os adiciona numa lista hostsToBeBalanced
		for (Host host : rankedHosts) {
			if (((long) host.getMemoryUsedInMib()) > clusterRAMusageAverage) {
				hostsToBeBalanced.add(host);
			} else {
				possibleTargets.add(host);
			}
		}
		
		//Caso tenha hosts para serem balanceados
		if (hostsToBeBalanced.size() > 0) {
			hostsToBeBalanced = sortHostsByRAMusageDecreasing(hostsToBeBalanced);	//Os primeiros estão mais sobrecarregados
			possibleTargets = sortHostsByRAMusageAscending(possibleTargets);			//Os primeiros são os menos sobrecarregados
			
			for (Host host : hostsToBeBalanced) {								//HostsComMigrações
				Set<VirtualMachine> setVms = host.getVirtualMachines();
				List<VirtualMachine> vms = new ArrayList<VirtualMachine>(setVms);	//vmsAtivas
				vms = sortVMsByRAMallocatedAscending(vms);							//ordena vmsAtivas por alocação de RAM
				
				vmsMigration:
				for (VirtualMachine vm : vms) {	
					
					searchTarget:
					for (Host targetHost : possibleTargets) {
						if (!verifyPossibleOverload(targetHost, vm, clusterRAMusageAverage)) {	//Se o host não ficar acima da média, passar a VM para ele
							mapOfMigrations.put(vm, targetHost);
							break searchTarget;
						}
					}
					
					if ((long) host.getMemoryUsedInMib() < clusterRAMusageAverage) {	//Não precisa mais migrar VMs pois já balanceou
						break vmsMigration;
					}
					
				}
				
			}
			
		}
		
    	return mapOfMigrations;
    }
	

    /*
	public void balanceClusterByRAMusage(List<Host> hostsAtivos, long clusterRAMusageAverage){
		List<Host> possibleTargets = new ArrayList<>();
		List<Host> hostsToBeBalanced = new ArrayList<>();
		
		//Verifica os hosts que precisam ser balanceados e os adiciona numa lista hostsToBeBalanced
		for (Host host : hostsAtivos) {
			if (((long) host.getMemoryUsedInMib()) > clusterRAMusageAverage) {
				hostsToBeBalanced.add(host);
			} else {
				possibleTargets.add(host);
			}
		}
		
		//Caso tenha hosts para serem balanceados
		if (hostsToBeBalanced.size() > 0) {
			hostsToBeBalanced = sortHostsByRAMusageDecreasing(hostsToBeBalanced);	//Os primeiros estão mais sobrecarregados
			possibleTargets = sortHostsByRAMusageAscending(possibleTargets);			//Os primeiros são os menos sobrecarregados
			
			for (Host host : hostsToBeBalanced) {								//HostsComMigrações
				Set<VirtualMachine> setVms = host.getVirtualMachines();
				List<VirtualMachine> vms = new ArrayList<VirtualMachine>(setVms);	//vmsAtivas
				vms = sortVMsByRAMallocatedAscending(vms);							//ordena vmsAtivas por alocação de RAM
				
				vmsMigration:
				for (VirtualMachine vm : vms) {						
					searchTarget:
					for (Host targetHost : possibleTargets) {
						if (!verifyPossibleOverload(targetHost, vm, clusterRAMusageAverage)) {
							//migraVM
							break searchTarget;
						}
					}
					
					if ((long) host.getMemoryUsedInMib() < clusterRAMusageAverage) {	//Não precisa mais migrar VMs pois já balanceou
						break vmsMigration;
					}
				}
			}
		}
		
	}
	*/

    void calculateClusterRAMaverageUsage(List<Host> activeHosts){
    	if (activeHosts.size() > 0) {
        	long totalUsage = 0;
        	for (Host host : activeHosts) {
    			totalUsage += host.getMemoryUsedInMib();
    		}
        	
        	clusterRAMusageAverage = totalUsage/activeHosts.size();	
		}
    }
    
    
	/**
	 * Sort a list of hosts in decreasing order of RAM usage for each host.
	 * @param hostsToBeSorted list of hosts to be sorted
	 * @return returns a list of hosts sorted in decreasing order 
	 */
	protected List<Host> sortHostsByRAMusageDecreasing(List<Host> hostsToBeSorted){
		List<Host> auxiliarList = hostsToBeSorted;
		Collections.sort(auxiliarList, new HostComparator());
		Collections.reverse(auxiliarList);
		return auxiliarList;		
	}

	/**
	 * Sort a list of hosts in ascending order of RAM usage for each host.
	 * @param hostsToBeSorted list of hosts to be sorted
	 * @return returns a list of hosts sorted in ascending order 
	 */
	protected List<Host> sortHostsByRAMusageAscending(List<Host> hostsToBeSorted){
		List<Host> auxiliarList = hostsToBeSorted;
		Collections.sort(auxiliarList, new HostComparator());
		return auxiliarList;
	}

	/**
	 * Sort a list of virtual machines in ascending order of allocated RAM for each virtual machine.
	 * @param vmsToBeSorted list of virtual machines to be sorted
	 * @return returns a list of virtual machines sorted in ascending order 
	 */
	protected List<VirtualMachine> sortVMsByRAMallocatedAscending(List<VirtualMachine> vmsToBeSorted){
		List<VirtualMachine> auxiliarList = vmsToBeSorted;
		Collections.sort(auxiliarList, new VirtualMachineComparator());
		return auxiliarList;
	}
	
	/**
	 * Verify the possibility of a given host to get RAM overloaded by migration of a given virtual machine.
	 * @param host candidate host to receive the virtual machine
	 * @param vm candidate virtual machine to be migrated to the given host.
	 * @param clusterAverageRAMusage is used as a measure to know if the host RAM usage will be over the cluster RAM usage average
	 * @return returns true if the host will get overloaded with the migration of the virtual machine, or returns false otherwise
	 */
	protected boolean verifyPossibleOverload(Host host, VirtualMachine vm, long clusterAverageRAMusage){
		long vmRAMallocated = vm.getVmServiceOffering().getMemoryInMegaByte();
		long hostRAMusage = (long) host.getMemoryUsedInMib();
		
		if ((hostRAMusage + vmRAMallocated) > clusterAverageRAMusage) {
			return true;
		} else {
			return false;
		}
	}
}


