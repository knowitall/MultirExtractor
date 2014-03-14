package edu.washington.multir.hadoop;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;


import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reducer;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.TextInputFormat;
import org.apache.hadoop.mapred.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

public class HadoopGetFeatureCount implements Tool{

	private Configuration conf;

	public HadoopGetFeatureCount(){
		conf = new Configuration();
		setConf(conf);
	}
	@Override
	public Configuration getConf() {
		return conf;
	}

	@Override
	public void setConf(Configuration arg0) {
		conf = arg0;
	}

	public static class Map extends MapReduceBase implements Mapper<LongWritable,Text,Text,IntWritable>{

		@Override
		public void map(LongWritable key, Text value,
				OutputCollector<Text, IntWritable> collector, Reporter reporter)
				throws IOException {
			String line = value.toString();
			String[] features = line.split("\\t");
			Set<String> featureSet = new HashSet<String>();
			for(int i = 4; i < features.length; i++){
				featureSet.add(features[i]);
			}
			for(String f: featureSet){
				collector.collect(new Text(f), new IntWritable(1));
			}
		}
	
	}
	
	public static class Reduce extends MapReduceBase implements Reducer<Text,IntWritable,Text,IntWritable>{

		@Override
		public void reduce(Text key, Iterator<IntWritable> values,
				OutputCollector<Text, IntWritable> collector, Reporter reporter)
				throws IOException {
			Integer count = 0;

			while(values.hasNext()){
				count += values.next().get();
			}
			collector.collect(key, new IntWritable(count));
		}
		
	}

	
	@Override
	public int run(String[] args) throws Exception {
		
		Configuration conf = getConf();
		JobConf job = new JobConf(conf,HadoopGetFeatureCount.class);
		
		
		//process command line options
		Path in = new Path(args[0]);
		Path out = new Path(args[1]);
		
		job.setJobName("hadoop-MultirFeatureCount");
		job.setInputFormat(TextInputFormat.class);
		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(IntWritable.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(IntWritable.class);
		job.setOutputFormat(TextOutputFormat.class);
		job.setMapperClass(Map.class);
		job.setReducerClass(Reduce.class);
		FileInputFormat.setInputPaths(job, in);
		FileOutputFormat.setOutputPath(job, out);
		job.setNumMapTasks(Integer.parseInt(args[2]));
		job.setNumReduceTasks(Integer.parseInt(args[3]));
		JobClient.runJob(job);
		return 0;
	}

	public static void main(String[] args) throws Exception{
		int res = ToolRunner.run(new HadoopGetFeatureCount(), args);
	}

}
