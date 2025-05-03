@echo off
setlocal

:: Ask for commit message
set /p COMMENT="Enter your commit message: "

:: Confirm once
set /p CONFIRM1="Are you sure you want to commit with this message? (yes/no): "
if /i not "%CONFIRM1%"=="yes" (
    echo Aborted on first confirmation.
    exit /b
)

:: Confirm twice
set /p CONFIRM2="Are you REALLY sure you want to FORCE PUSH this? (yes/no): "
if /i not "%CONFIRM2%"=="yes" (
    echo Aborted on second confirmation.
    exit /b
)

:: Execute Git commands
git add .
git commit -m "%COMMENT%"
git push -f origin main

echo.
echo *** Force push complete. GitHub has been scorched. ***
