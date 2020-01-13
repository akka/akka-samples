sbt universal:packageBin
rm -rf akka-sample-cqrs-scala-1.0 &&  unzip ./target/universal/akka-sample-cqrs-scala-1.0.zip && rsync -vr -e "ssh -i ~/.ssh/akka-cassandra.pem"  ./akka-sample-cqrs-scala-1.0/ ubuntu@3.121.40.252:/home/ubuntu/app/ --delete
