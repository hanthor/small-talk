if ! command -v gradle-profiler &> /dev/null
then
    echo "gradle-profiler could not be found https://github.com/gradle/gradle-profiler"
    exit
fi

gradle-profiler \
  --benchmark \
  --project-dir . \
  --scenario-file tools/benchmark/benchmark.profile \
  --output-dir benchmark-out/output \
  --gradle-user-home benchmark-out/gradle-home \
  --warmups 3 \
  --iterations 3 \
  $1