package org.prowl.torque.remote;
interface ITorqueService {
 String getVersion();
 String[] getPIDList();
 String[] getPIDInformation(in String[] pids);
 String[] getPIDValues(in String[] pids);
}