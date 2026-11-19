@echo off
:: Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
:: for details. All rights reserved. Use of this source code is governed by a
:: BSD-style license that can be found in the LICENSE file.
setlocal

echo This script has been marked as unused and will be deleted unless somebody is against it. 1>&2
exit /b 1

PATH=%~dp0;%PATH%

python "%~dp0\test.py" %*