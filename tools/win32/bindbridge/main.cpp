#include "BindView.h"
#include <windows.h>
#include <stdio.h>
#include <shellapi.h>
#include <string>

using namespace std;

int bindToBridge(LPWSTR deviceId, BOOL fEnable) {

	LPWSTR lpszInfId = L"ms_bridge";

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
		wprintf(L"Usage: bindbridge <deviceId> <bind|unbind>\n");
		return 1;
	}
	
	if (nArgs != 3) {
		wprintf(L"Usage: bindbridge <deviceId> <bind|unbind>\n");
		return 1;
	}

	BOOL bind;
	if (lstrcmp(szArglist[2], L"bind") == 0) {
		bind = TRUE;
	} else if (lstrcmp(szArglist[2], L"unbind") == 0) {
		bind = FALSE;
	} else {
		wprintf(L"Usage: bindbridge <deviceId> <bind|unbind>\n");
		return 1;
	}

	if (bind) {
		wprintf(L"Binding %s to bridge...\n", szArglist[1]);
	} else {
		wprintf(L"Unbinding %s from bridge...\n", szArglist[1]);
	}
	
	int ret = bindToBridge(szArglist[1], bind);
	
	if (bind) {
		wprintf(L"Device %s bound to bridge.\n", szArglist[1]);
	} else {
		wprintf(L"Device %s unbound from bridge.\n", szArglist[1]);
	}

	LocalFree(szArglist);

	return ret;
}