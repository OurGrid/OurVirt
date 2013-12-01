//+---------------------------------------------------------------------------
//
//  Microsoft Windows
//  Copyright (C) Microsoft Corporation, 2001.
//
//  File:       B I N D I N G . C P P
//
//  Contents:   Functions to illustrate
//              o How to enumerate binding paths.
//              o How to enumerate binding interfaces.
//              o How to enable/disable bindings.
//
//  Notes:      
//
//  Author:     Alok Sinha    15-May-01
//
//----------------------------------------------------------------------------

#include "bindview.h"


int EnableBindingPath(_In_ LPWSTR lpszInfId, _In_ LPWSTR lpszPathToken, _In_ BOOL fEnable) {
	INetCfg              *pnc;
	INetCfgBindingPath   *pncbp;
	LPWSTR               lpszApp;
	HRESULT              hr;
	int	ret = 0;

	hr = HrGetINetCfg(TRUE, APP_NAME, &pnc, &lpszApp);

	if (hr == S_OK) {
		pncbp = FindBindingPath(pnc, lpszInfId, lpszPathToken);

		if (pncbp) {
			hr = pncbp->Enable(fEnable);
			if (hr == S_OK) {
				hr = pnc->Apply();
				if (hr != S_OK) {
					wprintf(L"Failed to apply changes to the binding path.\n");
					ret = 1;
				}
			} else {
				if (fEnable) {
					wprintf(L"Failed to enable the binding path.\n");
					ret = 1;
				} else {
					wprintf(L"Failed to disable the binding path.\n");
					ret = 1;
				}
			}
			ReleaseRef(pncbp);
		}
		HrReleaseINetCfg(pnc, TRUE);
	} else {
		if ((hr == NETCFG_E_NO_WRITE_LOCK) && lpszApp) {
			wprintf(L"%s currently holds the lock, try later.\n", lpszApp);
			CoTaskMemFree(lpszApp);
			ret = 1;
		} else {
			wprintf(L"Couldn't get the notify object interface.\n");
			ret = 1;
		}
	}
    return ret;
}

INetCfgBindingPath *
FindBindingPath (
    INetCfg *pnc,
    _In_ LPWSTR lpszInfId,
    _In_ LPWSTR lpszPathTokenSelected)
{
    INetCfgComponent       *pncc = NULL;
    IEnumNetCfgBindingPath *pencbp = NULL;
    INetCfgBindingPath     *pncbp = NULL;
    LPWSTR                 lpszPathToken;
    HRESULT                hr;
    BOOL                   fFound;


    fFound = FALSE;

    //
    // Get the component reference.
    //

    hr = pnc->FindComponent( lpszInfId,
                             &pncc );

    if ( hr == S_OK ) {
     
        hr = HrGetBindingPathEnum( pncc,
                                   EBP_BELOW,
                                   &pencbp );
        if ( hr == S_OK ) {

            hr = HrGetFirstBindingPath( pencbp,
                                        &pncbp );

            // Enumerate each binding path and find the one
            // whose path token matches the specified one.
            //

            while ( !fFound && (hr == S_OK) ) {

                hr = pncbp->GetPathToken( &lpszPathToken );

                if ( hr == S_OK ) {
                    fFound = !wcscmp( lpszPathToken,
                                       lpszPathTokenSelected );

                    CoTaskMemFree( lpszPathToken );
                }

                if ( !fFound ) {
                    ReleaseRef( pncbp );

                    hr = HrGetNextBindingPath( pencbp,
                                               &pncbp );
                }
            }

            ReleaseRef( pencbp );
        }
        else {
			wprintf(L"Couldn't get the binding path enumerator interface." );
        }
    }
    else {
		wprintf(L"Couldn't get an interface pointer to %s.",
                lpszInfId );
    }

    return (fFound) ? pncbp : NULL;
}
