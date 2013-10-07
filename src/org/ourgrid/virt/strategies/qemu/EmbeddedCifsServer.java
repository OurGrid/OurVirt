package org.ourgrid.virt.strategies.qemu;

import java.net.InetAddress;
import java.util.Collection;

import org.alfresco.config.ConfigElement;
import org.alfresco.jlan.debug.DebugConfigSection;
import org.alfresco.jlan.server.auth.CifsAuthenticator;
import org.alfresco.jlan.server.auth.UserAccount;
import org.alfresco.jlan.server.auth.UserAccountList;
import org.alfresco.jlan.server.config.CoreServerConfigSection;
import org.alfresco.jlan.server.config.GlobalConfigSection;
import org.alfresco.jlan.server.config.SecurityConfigSection;
import org.alfresco.jlan.server.config.ServerConfiguration;
import org.alfresco.jlan.server.filesys.DiskDeviceContext;
import org.alfresco.jlan.server.filesys.DiskSharedDevice;
import org.alfresco.jlan.server.filesys.FilesystemsConfigSection;
import org.alfresco.jlan.smb.server.CIFSConfigSection;
import org.alfresco.jlan.smb.server.SMBServer;
import org.alfresco.jlan.smb.server.disk.JavaFileDiskDriver;
import org.ourgrid.virt.model.SharedFolder;

public class EmbeddedCifsServer {

	// Default memory pool settings
	
	private static final int[] DefaultMemoryPoolBufSizes  = { 256, 4096, 16384, 65536 };
	private static final int[] DefaultMemoryPoolInitAlloc = {  20,   20,     5,     5 };
	private static final int[] DefaultMemoryPoolMaxAlloc  = { 100,   50,    50,    50 };
	
	// Default thread pool size
	
	private static final int DefaultThreadPoolInit	= 25;
	private static final int DefaultThreadPoolMax	= 50;
	
	
	public static SMBServer create(Collection<SharedFolder> sharedFolders, String user, 
			String password, Integer port) throws Exception {
		ServerConfiguration cfg = new ServerConfiguration("ourvirt-share");
		
		FilesystemsConfigSection fsConfig = new FilesystemsConfigSection(cfg);
		
		for (SharedFolder sharedFolder : sharedFolders) {
			JavaFileDiskDriver diskDriver = new JavaFileDiskDriver();
			DiskDeviceContext ctx = new DiskDeviceContext(sharedFolder.getHostpath(), sharedFolder.getName());
			DiskSharedDevice dsd = new DiskSharedDevice(sharedFolder.getName(), diskDriver, ctx);
			ctx.startFilesystem(dsd);
			fsConfig.addShare(dsd);
		}
		
		cfg.addConfigSection(fsConfig);
		
		SecurityConfigSection secConfig = new SecurityConfigSection(cfg);
		UserAccountList ual = new UserAccountList();
		UserAccount ua = new UserAccount(user, password);
		ual.addUser(ua);
		secConfig.setUserAccounts(ual);
		secConfig.setUsersInterface(
				"org.alfresco.jlan.server.auth.DefaultUsersInterface", 
				new ConfigElement("foo", "bar"));
		cfg.addConfigSection(secConfig);
		
		CIFSConfigSection cifsConfig = new CIFSConfigSection(cfg);
		cifsConfig.setHostAnnouncer(false);
		cifsConfig.setWin32HostAnnouncer(false);
		cifsConfig.setNetBIOSSMB(false);
		cifsConfig.setWin32NetBIOS(false);
		cifsConfig.setAuthenticator(
				"org.alfresco.jlan.server.auth.LocalAuthenticator", 
				new ConfigElement("foo", "bar"), 
				CifsAuthenticator.SHARE_MODE, true);
		cifsConfig.setTcpipSMB(true);
		cifsConfig.setSMBBindAddress(InetAddress.getByName("127.0.0.1"));
		cifsConfig.setTcpipSMBPort(port);
		cifsConfig.setServerName("OurVirtShare");
		cifsConfig.setDomainName("OurVirtShare");
		cifsConfig.setSocketTimeout(0);
		cfg.addConfigSection(cifsConfig);
		
		CoreServerConfigSection coreConfig = new CoreServerConfigSection(cfg);
		coreConfig.setMemoryPool(DefaultMemoryPoolBufSizes,	
				DefaultMemoryPoolInitAlloc, DefaultMemoryPoolMaxAlloc);
		coreConfig.setThreadPool( DefaultThreadPoolInit, DefaultThreadPoolMax);
		cfg.addConfigSection(coreConfig);
		
		GlobalConfigSection gcs = new GlobalConfigSection(cfg);
		gcs.setTimeZoneOffset(0);
		cfg.addConfigSection(gcs);
		
		DebugConfigSection ds = new DebugConfigSection(cfg);
		ds.setDebug("org.alfresco.jlan.debug.ConsoleDebug", 
				new ConfigElement("foo", "bar"));
		cfg.addConfigSection(ds);
		
		return new SMBServer(cfg);
	}
}
