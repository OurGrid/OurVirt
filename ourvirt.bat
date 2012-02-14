setlocal
set LIB=%~dp0\lib
java -Djava.ext.dirs=%LIB% org/ourgrid/virt/Main %*
endlocal