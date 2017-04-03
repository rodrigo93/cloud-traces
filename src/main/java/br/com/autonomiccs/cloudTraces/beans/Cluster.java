/*
 * Cloud traces
 * Copyright (C) 2016 Autonomiccs, Inc.
 *
 * Licensed to the Autonomiccs, Inc. under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The Autonomiccs, Inc. licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package br.com.autonomiccs.cloudTraces.beans;

import java.util.ArrayList;
import java.util.List;

public class Cluster extends ComputingResource {

    private List<Host> hosts = new ArrayList<>();

    public Cluster(String id) {
        super(id);
    }

    public List<Host> getHosts() {
        return hosts;
    }

    @Override
    public String toString() {
        return String.format("Cluster %s, #hosts[%d]", super.toString(), hosts.size());
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        Cluster clone = (Cluster)super.clone();
        clone.hosts = new ArrayList<>(this.hosts);
        return clone;
    }
    
    public double getAverageRAMusage() {
    	double hostsRAMusage = getMemoryUsedInMib() / hosts.size();
    	
//		for (Host host : hosts) {
//			for (VirtualMachine vm : host.getVirtualMachines()) {
//				hostsRAMusage = hostsRAMusage + vm.getVmServiceOffering().getMemoryInMegaByte();
//			}
//		}
		
//		hostsRAMusage = hostsRAMusage / hosts.size();
		
		return hostsRAMusage;
	}
}
