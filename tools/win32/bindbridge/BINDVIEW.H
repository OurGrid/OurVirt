//+---------------------------------------------------------------------------
//
//  Microsoft Windows
//  Copyright (C) Microsoft Corporation, 2001.
//
//  File:       B I N D V I E W . H
//
//  Contents:   Function Prototypes
//
//  Notes:      
//
//  Author:     Alok Sinha    15-May-01
//
//----------------------------------------------------------------------------

#ifndef _BINDVIEW_H_INCLUDED

#define _BINDVIEW_H_INCLUDED

#include <stdio.h>
#include <stdlib.h>
#include <windows.h>
#include <windowsx.h>

#include "NetCfgAPI.h"
#include <strsafe.h>

__user_code;                // Annotation to specify PreFast analysis mode

#define celems(_x)          (sizeof(_x) / sizeof(_x[0]))

#define ID_STATUS           100
#define APP_NAME            L"BindBridge"

//
// Functions defined in binding.cpp
//

int EnableBindingPath(
	_In_ LPWSTR lpszInfId,
	_In_ LPWSTR lpszTokenPath,
	BOOL fEnable);

INetCfgBindingPath *FindBindingPath (INetCfg *pnc,
                                     _In_ LPWSTR lpszInfId,
                                     _In_ LPWSTR lpszPathTokenSelected);

#endif
