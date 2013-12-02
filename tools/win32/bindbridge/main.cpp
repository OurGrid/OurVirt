#include "BindView.h"
#include <windows.h>
#include <stdio.h>
#include <shellapi.h>
#include <string>

using namespace std;

int bindToBridge(LPWSTR lpszInfId, LPWSTR deviceId, BOOL fEnable) {

	wstring deviceIdW(deviceId);
	wstring lpszInfIdW(lpszInfId);
	wstring lpszTokenPathW = lpszInfIdW + L"->" + deviceIdW;

	LPWSTR lpszTokenPath = const_cast<LPWSTR>(lpszTokenPathW.c_str());
	
	return EnableBindingPath(lpszInfId, lpszTokenPath, fEnable);
}

int __cdecl main() {
	LPWSTR *szArglist;
	int nArgs;

	szArglist = CommandLineToArgvW(GetCommandLineW(), &nArgs);
	if (NULL == szArglist) {
		wprintf(L"Usage: bindbridge <bridgeId> <deviceId> <bind|unbind>\n");
		return 1;
	}
	
	if (nArgs != 4) {
		wprintf(L"Usage: bindbridge <bridgeId> <deviceId> <bind|unbind>\n");
		return 1;
	}

	BOOL bind;
	LPWSTR bridgeId = szArglist[1];
	LPWSTR devId = szArglist[2];
	LPWSTR op = szArglist[3];

	if (lstrcmp(op, L"bind") == 0) {
		bind = TRUE;
	} else if (lstrcmp(op, L"unbind") == 0) {
		bind = FALSE;
	} else {
		wprintf(L"Usage: bindbridge <bridgeId> <deviceId> <bind|unbind>\n");
		return 1;
	}

	if (bind) {
		wprintf(L"Binding %s to bridge %s...\n", devId, bridgeId);
	} else {
		wprintf(L"Unbinding %s from bridge %s...\n", devId, bridgeId);
	}
	
	int ret = bindToBridge(bridgeId, devId, bind);
	
	if (bind) {
		wprintf(L"Device %s bound to bridge %s.\n", devId, bridgeId);
	} else {
		wprintf(L"Device %s unbound from bridge %s.\n", devId, bridgeId);
	}

	LocalFree(szArglist);

	return ret;
}