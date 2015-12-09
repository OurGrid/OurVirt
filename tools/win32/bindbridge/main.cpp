#include "BindView.h"
#include <windows.h>
#include <stdio.h>
#include <shellapi.h>
#include <string>
#include <netcon.h>

using namespace std;

int bindToBridge(LPWSTR lpszInfId, LPWSTR deviceId, BOOL fEnable) {

	wstring deviceIdW(deviceId);
	wstring lpszInfIdW(lpszInfId);
	wstring lpszTokenPathW = lpszInfIdW + L"->" + deviceIdW;

	LPWSTR lpszTokenPath = const_cast<LPWSTR>(lpszTokenPathW.c_str());

	return EnableBindingPath(lpszInfId, lpszTokenPath, fEnable);
}

bool SearchRegistry(LPWSTR DeviceID, LPWSTR ClassID)
{
	HKEY hListKey = NULL;
	HKEY hKey = NULL;
	HRESULT hr = RegOpenKeyEx(HKEY_LOCAL_MACHINE, TEXT("SYSTEM\\CurrentControlSet\\Control\\Network\\{4D36E972-E325-11CE-BFC1-08002BE10318}"),
		0, KEY_READ, &hListKey);
	if (S_OK != hr) {
		printf("Failed to open adapter list key\n");
		return false;
	}
	FILETIME writtenTime;
	WCHAR keyNameBuf[1024];
	WCHAR keyNameBuf2[1024];
	DWORD keyNameBufSiz = 1024;
	DWORD crap;
	int i = 0;
	while (RegEnumKeyExW(hListKey, i++, keyNameBuf, &keyNameBufSiz, 0, NULL, NULL, &writtenTime) == S_OK) {
		_snwprintf_s(keyNameBuf2, 1024, TEXT("%s\\Connection"), keyNameBuf);
		hKey = NULL;
		hr = RegOpenKeyExW(hListKey, keyNameBuf2, 0, KEY_READ, &hKey);
		if (S_OK == hr) {
			keyNameBufSiz = 1024;
			if (RegQueryValueEx(hKey, TEXT("PnpInstanceID"), 0, &crap, (LPBYTE)keyNameBuf2, &keyNameBufSiz)
				== S_OK && strcmp((char*)keyNameBuf2, (char*)DeviceID) == 0) {
				wsprintf(ClassID, TEXT("%s"), keyNameBuf);
				return true;
			}
			RegCloseKey(hKey);
		}
		keyNameBufSiz = 512;
	}
	RegCloseKey(hListKey);
	return false;
}

HRESULT CheckBinding(INetSharingManager * pNSM, LPWSTR devClassID)
{
	// add a port mapping to every firewalled or shared connection
	INetSharingEveryConnectionCollection * pNSECC = NULL;
	HRESULT hr = pNSM->get_EnumEveryConnection(&pNSECC);
	if (S_OK != hr) {
		wprintf(L"failed to get EveryConnectionCollection!\r\n");
		return hr;
	}
	// enumerate connections
	IEnumVARIANT * pEV = NULL;
	IUnknown * pUnk = NULL;
	hr = pNSECC->get__NewEnum(&pUnk);
	if (S_OK != hr) {
		return hr;
	}
	hr = pUnk->QueryInterface(__uuidof(IEnumVARIANT),
			(void**)&pEV);
	pUnk->Release();

	if (S_OK != hr) {
		return hr;
	}
	VARIANT v;
	VariantInit(&v);
	while (S_OK == pEV->Next(1, &v, NULL)) {
		if (V_VT(&v) != VT_UNKNOWN) {
			VariantClear(&v);
			continue;
		}
		INetConnection * pNC = NULL;
		hr = V_UNKNOWN(&v)->QueryInterface(__uuidof(INetConnection),
				(void**)&pNC);
		if (S_OK != hr) {
			wprintf(L"pNC is null\r\n");
			VariantClear(&v);
			continue;
		}

		NETCON_PROPERTIES* pProps = NULL;
		pNC->GetProperties(&pProps);
		GUID guid = pProps->guidId;
		/*
		 * This is for debugging
		 printf("Guid = {%08lX-%04hX-%04hX-%02hhX%02hhX-%02hhX%02hhX%02hhX%02hhX%02hhX%02hhX}\n",
		 guid.Data1, guid.Data2, guid.Data3,
		 guid.Data4[0], guid.Data4[1], guid.Data4[2], guid.Data4[3],
		 guid.Data4[4], guid.Data4[5], guid.Data4[6], guid.Data4[7]);
		 */
		GUID devGuid;
		CLSIDFromString(devClassID, &devGuid);
		if (IsEqualGUID(guid, devGuid)) {
			INetConnectionProps * pNCP = NULL;
			hr = pNSM->get_NetConnectionProps(pNC, &pNCP);
			if (S_OK != hr) {
				wprintf(L"failed to get NetConnectionProps!\r\n");
				pNC->Release();
				continue;
			}
			// check properties for firewalled or shared connection
			DWORD dwCharacteristics = 0;
			VARIANT_BOOL pbEnabled = 0;
			pNCP->get_Characteristics(&dwCharacteristics);
			/*
			 * This checking is not compulsory now
			 if (bind && (dwCharacteristics & NCCF_BRIDGED)) {
			 wprintf(L"Already bridged\n");
			 return -1;
			 }
			 */
			if (dwCharacteristics & NCCF_SHARED) {
				INetSharingConfiguration * pNSC = NULL;
				hr = pNSM->get_INetSharingConfigurationForINetConnection(pNC, &pNSC);
				pNSC->get_SharingEnabled(&pbEnabled);
				SHARINGCONNECTIONTYPE *pType = NULL;
				pNSC->get_SharingConnectionType(pType);
				// wprintf(L"sharing type: %S\n", pType);
			}
			if (pbEnabled < 0) {
				wprintf(L"cannot bind network device(%ls) to bridge device\nwhile it uses ICS(Internet Connection Sharing).",
                        pProps->pszwDeviceName);
				pNCP->Release();
				pNC->Release();
				VariantClear(&v);
				return -2;
			}
			pNCP->Release();
			pNC->Release();
		}
		VariantClear(&v);
	}
	pEV->Release();
	pNSECC->Release();
	return hr;
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

	CoInitialize(NULL);

	// init security to enum RAS connections
	CoInitializeSecurity(NULL, -1, NULL, NULL,
		RPC_C_AUTHN_LEVEL_PKT,
		RPC_C_IMP_LEVEL_IMPERSONATE,
		NULL, EOAC_NONE, NULL);

	INetSharingManager * pNSM = NULL;
	HRESULT hr = ::CoCreateInstance(__uuidof(NetSharingManager),
		NULL,
		CLSCTX_ALL,
		__uuidof(INetSharingManager),
		(void**)&pNSM);

	if (hr != S_OK) {
		wprintf(L"failed to create NetSharingManager instance\r\n");
		return -1;
	}

	if (!pNSM)
		wprintf(L"failed to create NetSharingManager object\r\n");
	else {
		LPWSTR devClassID = new WCHAR[512];
		SearchRegistry(devId, devClassID);
		HRESULT hr = CheckBinding(pNSM, devClassID);
		if (FAILED(hr)) {
			return -1;
		}
		pNSM->Release();

	}
	CoUninitialize();

	int ret = bindToBridge(bridgeId, devId, bind);

	if (bind) {
		wprintf(L"Device %s bound to bridge %s.\n", devId, bridgeId);
	} else {
		wprintf(L"Device %s unbound from bridge %s.\n", devId, bridgeId);
	}

	LocalFree(szArglist);

	return ret;
}
