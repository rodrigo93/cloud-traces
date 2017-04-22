package br.com.autonomiccs.cloudTraces.algorithms.management;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import br.com.autonomiccs.cloudTraces.beans.Host;
import br.com.autonomiccs.cloudTraces.beans.HostComparator;
import br.com.autonomiccs.cloudTraces.beans.VirtualMachine;
import br.com.autonomiccs.cloudTraces.beans.VirtualMachineComparator;

public class ClusterVMsBalancingByRAMusage extends ClusterAdministrationAlgorithmEmptyImpl {
	
	private double totalClusterRAM; 				//In MB
	protected long clusterRAMusageAverage = 0;	//In MB
	protected int numberOfVms = 0;
	
	
	private final static Logger logger = Logger.getLogger(ClusterVMsBalancingByRAMusage.class);
	
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
		calculateTotalResources(rankedHosts);		//Get total cluster RAM, number of VMs, hosts and cluster's RAM average usage
		
		//for (Host host : rankedHosts) {logger.info(String.format("HOST: RAM usage of host [%s] [%.0f]", host.getId(), host.getMemoryUsedInMib()));}
		
		//Verifica os hosts que precisam ser balanceados e os adiciona numa lista hostsToBeBalanced
		for (Host host : rankedHosts) {
			if (((long) host.getMemoryAllocatedInMib()) > clusterRAMusageAverage) {
				hostsToBeBalanced.add(host);
			} else {
				possibleTargets.add(host);
			}
		}
		
		//logger.info(String.format("Number of hosts to be balanced before heuristic [%d]", hostsToBeBalanced.size()));

		
		//Caso tenha hosts para serem balanceados
		if (hostsToBeBalanced.size() > 0) {
			hostsToBeBalanced = sortHostsByRAMusageDecreasing(hostsToBeBalanced);		//Os primeiros estão mais sobrecarregados
			possibleTargets = sortHostsByRAMusageAscending(possibleTargets);			//Os primeiros são os menos sobrecarregados
			
			//for (Host host : hostsToBeBalanced) {logger.info(String.format("TO BE BALANCED: [%s] RAM usage [%.0f]", host.getId(), host.getMemoryUsedInMib()));}
			//for (Host host : possibleTargets) {logger.info(String.format("POSSIBLE TARGET: [%s] ram usage [%.0f], allocated RAM [%.0f]", host.getId(), host.getMemoryUsedInMib(), (float)host.getTotalMemoryInMib()));}
			
			for (Host host : hostsToBeBalanced) {									//Hosts sobrecarregados
				Set<VirtualMachine> setVms = host.getVirtualMachines();
				List<VirtualMachine> vms = new ArrayList<VirtualMachine>(setVms);	//vmsAtivas
				vms = sortVMsByRAMallocatedAscending(vms);							//ordena vmsAtivas por alocação de RAM
				
				//for (VirtualMachine virtualMachine : vms) {logger.info(String.format("VIRTUAL MACHINE: [%s] allocated memory [%.0f]", virtualMachine.getVmId(), (float)virtualMachine.getVmServiceOffering().getMemoryInMegaByte()));}
				
				vmsMigration:
				for (VirtualMachine vm : vms) {	
					
					searchTarget:
					for (Host targetHost : possibleTargets) {
						if (!verifyPossibleOverload(targetHost, vm, clusterRAMusageAverage)) {	//Se o host não ficar acima da média, passar a VM para ele
							//logger.info(String.format("MIGRATION: Host [%s] will receive vm [%s] from Host [%s]", targetHost.getId(), vm.getVmId(), host.getId()));
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
	
    void calculateClusterRAMaverageUsage(List<Host> activeHosts){
    	if (activeHosts.size() > 0) {
        	long totalUsage = 0;
        	long totalClusterMemory = 0;
        	long allocatedClusterMemory = 0;
        	for (Host host : activeHosts) {
        		totalUsage += host.getMemoryAllocatedInMib();
        		totalClusterMemory += host.getTotalMemoryInMib();
        		allocatedClusterMemory += host.getMemoryUsedInMib();
    		}
        	
        	clusterRAMusageAverage = allocatedClusterMemory/activeHosts.size();	
        	
        	//logger.info(String.format("CLUSTER: RAM allocated by hosts [%d]", totalUsage));
        	//logger.info(String.format("CLUSTER: Total RAM [%d]", totalClusterMemory));
        	//logger.info(String.format("CLUSTER: RAM usage average [%.02f]", (float)clusterRAMusageAverage));
        	//logger.info(String.format("CLUSTER: RAM usage by hosts [%.02f]", (float)allocatedClusterMemory));
        	if (totalClusterRAM > 0) {
        		double usageInPercentage = toPercentage(clusterRAMusageAverage, (long)totalClusterRAM);
        		//logger.info(String.format("CLUSTER: RAM usage average in percentage [%.2f]%%", usageInPercentage));
			}
        	
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
	
	private void calculateTotalResources(List<Host> hosts){
		long totalClusterRAMaux = 0;
		for (Host host : hosts) {
			totalClusterRAMaux += host.getTotalMemoryInMib();
			numberOfVms += host.getVirtualMachines().size();
		}
		totalClusterRAM = (double)totalClusterRAMaux;
		//logger.info(String.format("CLUSTER: RAM capacity [%d], number of hosts [%s], number of VMs [%d]", totalClusterRAMaux, hosts.size(), numberOfVms));
		calculateClusterRAMaverageUsage(hosts);
	}
	
	private double toPercentage(long valueOne, long totalValue){
		return ((double)valueOne/(double)totalValue)*100;
	}
}


