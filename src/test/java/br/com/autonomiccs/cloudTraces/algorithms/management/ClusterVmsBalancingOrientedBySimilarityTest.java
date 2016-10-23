package br.com.autonomiccs.cloudTraces.algorithms.management;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import br.com.autonomiccs.cloudTraces.algorithms.management.ClusterVmsBalancingOrientedBySimilarity.ComputingResourceIdAndScore;
import br.com.autonomiccs.cloudTraces.beans.Host;
import br.com.autonomiccs.cloudTraces.beans.VirtualMachine;
import br.com.autonomiccs.cloudTraces.beans.VmServiceOffering;

public class ClusterVmsBalancingOrientedBySimilarityTest {

    private ClusterVmsBalancingOrientedBySimilarity spy;

    private static final long NUMBER_OF_BYTES_IN_ONE_MEGA_BYTE = 1024l;
    private static final String COSINE_SIMILARITY_WITH_HOST_VM_RATIO = "cosineSimilarityWithHostVmRatio";
    protected static final String COSINE_SIMILARITY = "cosineSimilarity";

    @Before
    public void setup() {
        spy = Mockito.spy(new ClusterVmsBalancingOrientedBySimilarity());
    }

    @Test
    public void rankHostsTest() {
        List<ComputingResourceIdAndScore> dummyList = new ArrayList<>();
        Mockito.doReturn(dummyList).when(spy).sortHostsByCpuUsage(Mockito.anyListOf(Host.class));
        Mockito.doReturn(dummyList).when(spy).sortHostsByMemoryUsage(Mockito.anyListOf(Host.class));
        Mockito.doReturn(dummyList).when(spy).calculateHostsScore(Mockito.anyListOf(ComputingResourceIdAndScore.class), Mockito.anyListOf(ComputingResourceIdAndScore.class));

        List<Host> hosts = spy.rankHosts(createHostsForTest());

        Assert.assertNotNull(hosts);
        InOrder inOrder = Mockito.inOrder(spy);
        inOrder.verify(spy).sortHostsByCpuUsage(Mockito.anyListOf(Host.class));
        inOrder.verify(spy).sortHostsByMemoryUsage(Mockito.anyListOf(Host.class));
        inOrder.verify(spy).calculateHostsScore(Mockito.anyListOf(ComputingResourceIdAndScore.class), Mockito.anyListOf(ComputingResourceIdAndScore.class));
    }

    @Test
    public void calculateHostsScoreTest() {
        List<ComputingResourceIdAndScore> hostsSortedByCpuUsage = spy.sortHostsByCpuUsage(createHostsForTest());
        List<ComputingResourceIdAndScore> hostsSortedByMemoryUsage = spy.sortHostsByMemoryUsage(createHostsForTest());
        List<ComputingResourceIdAndScore> hostsWithScore = spy.calculateHostsScore(hostsSortedByCpuUsage, hostsSortedByMemoryUsage);

        assertHostsIdsSequence("0", "2", "1", hostsWithScore.get(0).getId(), hostsWithScore.get(1).getId(), hostsWithScore.get(2).getId());

        Assert.assertEquals(1, (int) hostsWithScore.get(0).getScore());
        Assert.assertEquals(1, (int) hostsWithScore.get(1).getScore());
        Assert.assertEquals(4, (int) hostsWithScore.get(2).getScore());
    }

    @Test
    public void sortHostsByCpuUsageTest() {
        List<ComputingResourceIdAndScore> sortedResources = spy.sortHostsByCpuUsage(createHostsForTest());
        assertHostsIdsSequence("0", "2", "1", sortedResources.get(0).getId(), sortedResources.get(1).getId(), sortedResources.get(2).getId());
    }

    @Test
    public void sortHostsByMemoryUsageTest() {
        List<ComputingResourceIdAndScore> sortedResources = spy.sortHostsByMemoryUsage(createHostsForTest());
        assertHostsIdsSequence("2", "0", "1", sortedResources.get(0).getId(), sortedResources.get(1).getId(), sortedResources.get(2).getId());
    }

    @Test
    public void sortHostUpwardScoreBasedOnSortedListOfComputingResourceIdAndScoreTest() {
        List<ComputingResourceIdAndScore> sortedResources = createComputingResourcesIdAndScoreForTest();
        spy.sortComputingResourceUpwardScore(sortedResources);
        List<Host> sortedHosts = spy.sortHostUpwardScoreBasedOnSortedListOfComputingResourceIdAndScore(sortedResources, createHostsForTest());
        assertHostsIdsSequence("1", "2", "0", sortedHosts.get(0).getId(), sortedHosts.get(1).getId(), sortedHosts.get(2).getId());
    }

    @Test
    public void sortComputingResourceUpwardScoreTest() {
        List<ComputingResourceIdAndScore> sortedResources = createComputingResourcesIdAndScoreForTest();
        spy.sortComputingResourceUpwardScore(sortedResources);
        assertHostsIdsSequence("1", "2", "0", sortedResources.get(0).getId(), sortedResources.get(1).getId(), sortedResources.get(2).getId());
    }

    @Test
    public void simulateMapOfMigrationsTest() {
        List<Host> rankedHosts = new ArrayList<>();
        rankedHosts.add(createEmptyHost("1", 16000, 16 * NUMBER_OF_BYTES_IN_ONE_MEGA_BYTE));
        rankedHosts.get(0).addVirtualMachine(createBigVm("1"));
        rankedHosts.get(0).addVirtualMachine(createBigVm("2"));

        rankedHosts.add(createEmptyHost("2", 32000, 32 * NUMBER_OF_BYTES_IN_ONE_MEGA_BYTE));

        rankedHosts.add(createEmptyHost("3", 16000, 16 * NUMBER_OF_BYTES_IN_ONE_MEGA_BYTE));
        Set<VirtualMachine> vms3 = new HashSet<>();
        rankedHosts.get(2).addVirtualMachine(createSmallVm("3"));
        rankedHosts.get(2).addVirtualMachine(createSmallVm("4"));
        rankedHosts.get(2).addVirtualMachine(createBigVm("5"));

        spy.calculateClusterStatistics(rankedHosts);
        Map<VirtualMachine, Host> mapOfMigrations = spy.simulateMapOfMigrations(spy.rankHosts(rankedHosts), spy.vmsAllocatedCpuInMhzMean, spy.vmsAllocatedMemoryInMibMean, 1,
                COSINE_SIMILARITY_WITH_HOST_VM_RATIO);

        Assert.assertNotNull(mapOfMigrations);
        Assert.assertEquals(3, mapOfMigrations.size());
    }

    @Test
    public void cosineSimilarityWithHostVmRatioStringTest() {
        Assert.assertEquals(COSINE_SIMILARITY_WITH_HOST_VM_RATIO, spy.COSINE_SIMILARITY_WITH_HOST_VM_RATIO);
    }

    @Test
    public void canReceiveVmTestExpectFalse() {
        spy.clusterCpuUsage = 0.2;
        spy.clusterMemoryUsage = 0.3;
        Host host = createEmptyHost("1", 8000, 8 * NUMBER_OF_BYTES_IN_ONE_MEGA_BYTE);
        host.setCpuAllocatedInMhz(4000);
        host.setMemoryAllocatedInBytes(4 * NUMBER_OF_BYTES_IN_ONE_MEGA_BYTE);
        try {
            boolean result = spy.canReceiveVm(host, createSmallVm(""), 1000, NUMBER_OF_BYTES_IN_ONE_MEGA_BYTE);
            Assert.assertFalse(result);
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void canReceiveVmTestExpectTrue() {
        spy.clusterCpuUsage = 0.5;
        spy.clusterMemoryUsage = 0.5;
        Host host = createEmptyHost("1", 8000,8 * NUMBER_OF_BYTES_IN_ONE_MEGA_BYTE);
        host.setCpuAllocatedInMhz(1000);
        host.setMemoryAllocatedInBytes(1 * NUMBER_OF_BYTES_IN_ONE_MEGA_BYTE);
        try {
            boolean result = spy.canReceiveVm(host, createSmallVm(""), 1000, NUMBER_OF_BYTES_IN_ONE_MEGA_BYTE);
            Assert.assertTrue(result);
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void calculateClusterUsagePercentageTest() {
        spy.calculateClusterUsage(createHostsForTest());
        Assert.assertTrue(0.5 == spy.clusterCpuUsage);
        Assert.assertTrue(0.2625 == spy.clusterMemoryUsage);
    }

    @Test
    public void sortVmsInDescendingOrderBySimilarityWithHostCandidateToReceiveVmsTest() {
        Host candidateToReceiveVms = createEmptyHost("0", 128000, 16 * NUMBER_OF_BYTES_IN_ONE_MEGA_BYTE);
        Host hostToBeOffloaded = createEmptyHost("0", 10000, 10 * NUMBER_OF_BYTES_IN_ONE_MEGA_BYTE);
        hostToBeOffloaded.addVirtualMachine(createCpuBoundVm("2"));
        hostToBeOffloaded.addVirtualMachine(createSmallVm("0"));
        hostToBeOffloaded.addVirtualMachine(createMediumVm("1"));

        List<VirtualMachine> vms = spy.sortVmsInDescendingOrderBySimilarityWithHostCandidateToReceiveVms(hostToBeOffloaded, candidateToReceiveVms, 2,
                COSINE_SIMILARITY_WITH_HOST_VM_RATIO);

        Assert.assertEquals("0", vms.get(0).getVmId());
        Assert.assertEquals("1", vms.get(1).getVmId());
        Assert.assertEquals("2", vms.get(2).getVmId());
    }

    @Test
    public void calculateVmsSimilarityTest() { //TODO enhance test
        Host candidateToReceiveVms = createEmptyHost("0", 4000, 64 * NUMBER_OF_BYTES_IN_ONE_MEGA_BYTE);
        List<VirtualMachine> vms = new ArrayList<VirtualMachine>();
        vms.add(createSmallVm("0"));
        vms.add(createMediumVm("1"));
        vms.add(createCpuBoundVm("2"));

        List<ComputingResourceIdAndScore> scores = spy.calculateSimilaritiesOfVmsWithHost(vms, candidateToReceiveVms, 2);
        List<ComputingResourceIdAndScore> scoresAlpha10 = spy.calculateSimilaritiesOfVmsWithHost(vms, candidateToReceiveVms, 10);
        List<ComputingResourceIdAndScore> scoresAlpha0 = spy.calculateSimilaritiesOfVmsWithHost(vms, candidateToReceiveVms, 0);

        Assert.assertTrue(scores.get(0).getScore() > scores.get(1).getScore());
        Assert.assertTrue(scores.get(1).getScore() > scores.get(2).getScore());
        Assert.assertTrue(scores.get(0).getScore() > scoresAlpha10.get(0).getScore());
        Assert.assertTrue(scoresAlpha0.get(1).getScore() > scoresAlpha0.get(2).getScore());
    }

    @Test
    public void updateHostResourcesTest() {
        VirtualMachine vm = createSmallVm("id");
        Host hostToBeOffloaded = createEmptyHost("0", 4000, 64 * NUMBER_OF_BYTES_IN_ONE_MEGA_BYTE);
        hostToBeOffloaded.addVirtualMachine(vm);
        Host candidateToReceiveVms = createEmptyHost("0", 4000, 64 * NUMBER_OF_BYTES_IN_ONE_MEGA_BYTE);

        spy.updateHostResources(hostToBeOffloaded, candidateToReceiveVms, vm);

        Assert.assertEquals(0, hostToBeOffloaded.getVirtualMachines().size());
        Assert.assertEquals(0, hostToBeOffloaded.getCpuAllocatedInMhz());
        Assert.assertEquals(0, hostToBeOffloaded.getMemoryAllocatedInBytes());

        Assert.assertEquals(1, candidateToReceiveVms.getVirtualMachines().size());
        Assert.assertTrue(candidateToReceiveVms.getVirtualMachines().contains(vm));
        Assert.assertEquals(1000, candidateToReceiveVms.getCpuAllocatedInMhz());
        Assert.assertEquals(1 * NUMBER_OF_BYTES_IN_ONE_MEGA_BYTE, candidateToReceiveVms.getMemoryAllocatedInBytes());
    }

    @Test
    @SuppressWarnings("static-access")
    public void numberOfBytesInOneMegaByteConstantTest() {
        Assert.assertEquals(NUMBER_OF_BYTES_IN_ONE_MEGA_BYTE, spy.NUMBER_OF_BYTES_IN_ONE_MEGA_BYTE);
    }

    private VirtualMachine createSmallVm(String id) {
        VmServiceOffering vmServiceOffering = configureVmServiceOffering(1000, 1, 1);
        VirtualMachine vm = new VirtualMachine();
        vm.setVmId(id);
        vm.setVmServiceOffering(vmServiceOffering);
        return vm;
    }

    private VirtualMachine createMediumVm(String id) {
        VmServiceOffering vmServiceOffering = configureVmServiceOffering(2000, 1, 2);
        VirtualMachine vm = new VirtualMachine();
        vm.setVmId(id);
        vm.setVmServiceOffering(vmServiceOffering);
        return vm;
    }

    private VirtualMachine createBigVm(String id) {
        VmServiceOffering vmServiceOffering = configureVmServiceOffering(2000, 2, 4);
        VirtualMachine vm = new VirtualMachine();
        vm.setVmId(id);
        vm.setVmServiceOffering(vmServiceOffering);
        return vm;
    }

    private VirtualMachine createCpuBoundVm(String id) {
        VmServiceOffering vmServiceOffering = configureVmServiceOffering(6000, 5, 2);
        VirtualMachine vm = new VirtualMachine();
        vm.setVmId(id);
        vm.setVmServiceOffering(vmServiceOffering);
        return vm;
    }

    private VmServiceOffering configureVmServiceOffering(int cpuSpeed, int numberOfCores, long memoryInMegaByte) {
        VmServiceOffering vmServiceOffering = new VmServiceOffering();
        vmServiceOffering.setCoreSpeed(cpuSpeed);
        vmServiceOffering.setNumberOfCores(numberOfCores);
        vmServiceOffering.setMemoryInMegaByte(memoryInMegaByte);
        return vmServiceOffering;
    }

    private List<Host> createHostsForTest() {
        List<Host> hosts = new ArrayList<>();
        Host h1 = createEmptyHost("0", 4000, 64 * NUMBER_OF_BYTES_IN_ONE_MEGA_BYTE);
        h1.setCpuAllocatedInMhz(1000);
        h1.setMemoryAllocatedInBytes(16 * NUMBER_OF_BYTES_IN_ONE_MEGA_BYTE);
        hosts.add(h1);

        Host h2 = createEmptyHost("1", 4000, 32 * NUMBER_OF_BYTES_IN_ONE_MEGA_BYTE);
        h2.setCpuAllocatedInMhz(3000);
        h2.setMemoryAllocatedInBytes(18 * NUMBER_OF_BYTES_IN_ONE_MEGA_BYTE);
        hosts.add(h2);

        Host h3 = createEmptyHost("2", 2000, 64 * NUMBER_OF_BYTES_IN_ONE_MEGA_BYTE);
        h3.setCpuAllocatedInMhz(1000);
        h3.setMemoryAllocatedInBytes(8 * NUMBER_OF_BYTES_IN_ONE_MEGA_BYTE);
        hosts.add(h3);

        return hosts;
    }

    private List<ComputingResourceIdAndScore> createComputingResourcesIdAndScoreForTest() {
        List<ComputingResourceIdAndScore> computingResources = new ArrayList<>();
        computingResources.add(spy.new ComputingResourceIdAndScore("0", 2));
        computingResources.add(spy.new ComputingResourceIdAndScore("1", 0));
        computingResources.add(spy.new ComputingResourceIdAndScore("2", 1));
        return computingResources;
    }

    private Host createEmptyHost(String id, long cpuTotalInMhz, long memoryTotalInBytes) {
        Host h = new Host(id);
        h.setTotalCpuPowerInMhz(cpuTotalInMhz);
        h.setTotalMemoryInBytes(memoryTotalInBytes);
        return h;
    }

    private void assertHostsIdsSequence(String expectedId0, String expectedId1, String expectedId2, String idResource0, String idResource1, String idResource2) {
        Assert.assertEquals(expectedId0, idResource0);
        Assert.assertEquals(expectedId1, idResource1);
        Assert.assertEquals(expectedId2, idResource2);
    }

}
