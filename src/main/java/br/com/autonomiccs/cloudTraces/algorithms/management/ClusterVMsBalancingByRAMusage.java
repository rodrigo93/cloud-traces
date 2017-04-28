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
	protected long clusterRAMallocatedAverage = 0;	//In MB
	protected int numberOfVms = 0;
    protected static final long NUMBER_OF_BYTES_IN_ONE_MEGA_BYTE = 1024l;
	
	
//	private final static Logger logger = Logger.getLogger(ClusterVMsBalancingByRAMusage.class);
	
    @Override
    public List<Host> rankHosts(List<Host> hosts) {
        return sortHostsByAllocatedRAMdecreasing(hosts);
    }

	/**
	 * Balance the cluster based on the RAM usage of each host in it.
	 * @param hostsAtivos list of active hosts in the cluster.
	 * @param clusterRAMallocatedAverage RAM average used by the hosts.
	 */
    @Override
    public Map<VirtualMachine, Host> mapVMsToHost(List<Host> rankedHosts) {
    	Map<VirtualMachine, Host> mapOfMigrations = new HashMap<>();
		List<Host> possibleTargets = new ArrayList<>();
		List<Host> hostsToBeBalanced = new ArrayList<>();
		calculateTotalResources(rankedHosts);
		
//		logger.info(String.format("CLUSTER: Allocated RAM average [%s]", clusterRAMallocatedAverage));
//		for (Host host : rankedHosts) {logger.info(String.format("HOST: Allocated RAM of host [%s] [%s]", host.getId(), host.getMemoryAllocatedInMib()));}
		
		//Verifica os hosts que precisam ser balanceados e os adiciona numa lista hostsToBeBalanced
		for (Host host : rankedHosts) {
			if ( host.getMemoryAllocatedInMib() > clusterRAMallocatedAverage ) {
				hostsToBeBalanced.add(host);
			} else {
				possibleTargets.add(host);
			}
		}
		
//		logger.info(String.format("Number of hosts to be balanced before heuristic [%d]", hostsToBeBalanced.size()));

		
		//Caso tenha hosts para serem balanceados
		if (hostsToBeBalanced.size() > 0) {
			hostsToBeBalanced = sortHostsByAllocatedRAMdecreasing(hostsToBeBalanced);		//Os primeiros estão mais sobrecarregados
			possibleTargets = sortHostsByAllocatedRAMascending(possibleTargets);			//Os primeiros são os menos sobrecarregados
			
//			for (Host host : hostsToBeBalanced) {logger.info(String.format("TO BE BALANCED: [%s] allocated RAM [%s], total RAM [%s]", host.getId(), host.getMemoryAllocatedInMib(), host.getTotalMemoryInMib()));}
//			for (Host host : possibleTargets) {logger.info(String.format("POSSIBLE TARGET: [%s] allocated RAM [%s], total memory [%s]", host.getId(), host.getMemoryAllocatedInMib(), host.getMemoryAllocatedInMib(), host.getTotalMemoryInMib()));}
			
			for (Host host : hostsToBeBalanced) {									//Hosts sobrecarregados
				Set<VirtualMachine> setVms = host.getVirtualMachines();
				List<VirtualMachine> vms = new ArrayList<VirtualMachine>(setVms);	//vmsAtivas
				vms = sortVMsByRAMallocatedAscending(vms);							//ordena vmsAtivas por alocação de RAM
				
//				for (VirtualMachine virtualMachine : vms) {logger.info(String.format("VIRTUAL MACHINE: [%s] allocated memory [%.0f]", virtualMachine.getVmId(), (float)virtualMachine.getVmServiceOffering().getMemoryInMegaByte()));}
				
				vmsMigration:
				for (VirtualMachine vm : vms) {	
					
					searchTarget:
					for (Host targetHost : possibleTargets) {
						if (verifyPossibleOverload(targetHost, vm, clusterRAMallocatedAverage) != true) {	//Se o host não ficar acima da média, passar a VM para ele
//							logger.info(String.format("MIGRATION: Host [%s] will receive vm [%s] with allocated RAM of [%s] from Host [%s]", targetHost.getId(), vm.getVmId(), vm.getVmServiceOffering().getMemoryInMegaByte(),host.getId()));
//							logger.info(String.format("TARGET HOST: RAM allocated before migration [%s], total RAM [%s]", targetHost.getMemoryAllocatedInMib(), targetHost.getTotalMemoryInMib()));
							
							updateHostResources(host, targetHost, vm);
//							vm.setHost(targetHost);

							mapOfMigrations.put(vm, targetHost);
							
//							logger.info(String.format("TARGET HOST: RAM allocated after migration [%s], total RAM [%s]", targetHost.getMemoryAllocatedInMib(), targetHost.getTotalMemoryInMib()));
							
							possibleTargets = sortHostsByAllocatedRAMascending(possibleTargets);			//Os primeiros são os menos sobrecarregados
							break searchTarget;
						}
					}
					
					if ((long) host.getMemoryAllocatedInMib() < clusterRAMallocatedAverage) {	//Não precisa mais migrar VMs pois já balanceou
						break vmsMigration;
					}
				}
				
			}
			
		} else {
//			logger.info(String.format("CLUSTER: No need to balance allocated RAM for now."));
		}
		
    	return mapOfMigrations;
    }
	
    /**
     * Updates host resources of two given hosts (source and target) moving a VM from the source to the target. 
     * @param hostToBeOffloaded	source host that will migrate its VM.
     * @param candidateToReceiveVms target host that will receive the VM.
     * @param vm VM to be migrated.
     */
    protected void updateHostResources(Host hostToBeOffloaded, Host candidateToReceiveVms, VirtualMachine vm) {
    	long vmCpuConfigurationInMhz = vm.getVmServiceOffering().getCoreSpeed() * vm.getVmServiceOffering().getNumberOfCores();
        long vmMemoryConfigurationInBytes = vm.getVmServiceOffering().getMemoryInMegaByte() * NUMBER_OF_BYTES_IN_ONE_MEGA_BYTE;
        
        hostToBeOffloaded.getVirtualMachines().remove(vm);
        hostToBeOffloaded.setCpuAllocatedInMhz(hostToBeOffloaded.getCpuAllocatedInMhz() - vmCpuConfigurationInMhz);
        hostToBeOffloaded.setMemoryAllocatedInBytes(hostToBeOffloaded.getMemoryAllocatedInBytes() - vmMemoryConfigurationInBytes);
        
        candidateToReceiveVms.addVirtualMachine(vm);
    }
    
    /**
     * Calculate the allocated RAM average by hosts in the cluster.
     * @param activeHosts list of hosts in the cluster.
     */
    void calculateClusterRAMaverageUsage(List<Host> activeHosts){
    	if (activeHosts.size() > 0) {
//        	long totalAllocatedRAM = 0;
//        	long totalClusterMemory = 0;
        	long allocatedClusterMemory = 0;
        	for (Host host : activeHosts) {
//        		totalAllocatedRAM += host.getMemoryAllocatedInMib();
//        		totalClusterMemory += host.getTotalMemoryInMib();
        		allocatedClusterMemory += host.getMemoryAllocatedInMib();
    		}
        	
        	clusterRAMallocatedAverage = allocatedClusterMemory/activeHosts.size();	
        	
//        	logger.info(String.format("CLUSTER: RAM allocated by hosts [%d]", totalAllocatedRAM));
//        	logger.info(String.format("CLUSTER: Total RAM [%d]", totalClusterMemory));
//        	logger.info(String.format("CLUSTER: RAM usage average [%.02f]", (float)clusterRAMallocatedAverage));
//        	logger.info(String.format("CLUSTER: RAM usage by hosts [%.02f]", (float)allocatedClusterMemory));
//        	if (totalClusterRAM > 0) {
//        		double usageInPercentage = toPercentage(clusterRAMallocatedAverage, (long)totalClusterRAM);
//        		logger.info(String.format("CLUSTER: RAM usage average in percentage [%.2f]%%", usageInPercentage));
//			}
        	
		}
    }
    
	/**
	 * Sort a list of hosts in decreasing order of allocated RAM for each host.
	 * @param hostsToBeSorted list of hosts to be sorted
	 * @return returns a list of hosts sorted in decreasing order 
	 */
	protected List<Host> sortHostsByAllocatedRAMdecreasing(List<Host> hostsToBeSorted){
		List<Host> auxiliarList = hostsToBeSorted;
		Collections.sort(auxiliarList, new HostComparator());
		Collections.reverse(auxiliarList);
		return auxiliarList;		
	}

	/**
	 * Sort a list of hosts in ascending order of allocated RAM for each host.
	 * @param hostsToBeSorted list of hosts to be sorted
	 * @return returns a list of hosts sorted in ascending order 
	 */
	protected List<Host> sortHostsByAllocatedRAMascending(List<Host> hostsToBeSorted){
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
	 * @param targetHost candidate host to receive the virtual machine
	 * @param vm candidate virtual machine to be migrated to the given host.
	 * @param clusterAllocatedRAMaverage is used as a measure to know if the host allocated RAM will be over the cluster allocated RAM average
	 * @return returns true if the host will get overloaded with the migration of the virtual machine, or returns false otherwise
	 */
	protected boolean verifyPossibleOverload(Host targetHost, VirtualMachine vm, long clusterAllocatedRAMaverage){
		long vmRAMallocated = vm.getVmServiceOffering().getMemoryInMegaByte();
		long hostRAMallocated = targetHost.getMemoryAllocatedInMib();
		long totalAllocatedRAM = vmRAMallocated + hostRAMallocated;
		
		if ((totalAllocatedRAM > clusterAllocatedRAMaverage) || targetHost.getTotalMemoryInMib() < vmRAMallocated + targetHost.getMemoryAllocatedInMib()) {
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * Calculates the total allocated RAM of the cluster based its hosts list.
	 * @param hosts list of hosts from the cluster.
	 */
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


