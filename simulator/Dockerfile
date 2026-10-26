# 설비 시뮬레이터 이미지. 기준정보 CSV(data/)가 필요해서 저장소 루트를 빌드 위치로 쓴다.
FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src
COPY simulator/MiniMes.Simulator/MiniMes.Simulator.csproj simulator/MiniMes.Simulator/
RUN dotnet restore simulator/MiniMes.Simulator/MiniMes.Simulator.csproj
COPY simulator/MiniMes.Simulator simulator/MiniMes.Simulator
RUN dotnet publish simulator/MiniMes.Simulator/MiniMes.Simulator.csproj -c Release -o /app --no-restore

FROM mcr.microsoft.com/dotnet/runtime:8.0
WORKDIR /app
COPY --from=build /app .
# RepoPaths가 실행 폴더에서 위로 올라가며 data/...를 찾으므로 /app/data에 둔다
COPY data/master/equipment.csv data/master/
COPY data/profile/downtime_events.csv data/profile/
ENV TZ=Asia/Seoul
ENTRYPOINT ["dotnet", "MiniMes.Simulator.dll"]
CMD ["--speed", "60"]
