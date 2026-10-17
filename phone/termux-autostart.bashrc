# ~/.bashrc -- DayToDay
# Start the local DayToDay server (inside proot Ubuntu) whenever Termux is
# opened, unless it is already listening on 127.0.0.1:4000.
if ! (exec 3<>/dev/tcp/127.0.0.1/4000) 2>/dev/null; then
  nohup /data/data/com.termux/files/home/run.sh >> /data/data/com.termux/files/home/server.log 2>&1 &
fi
