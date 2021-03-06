/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.gearpump.streaming.kafka.lib

import com.twitter.bijection.Injection
import kafka.common.TopicAndPartition
import kafka.producer.{KeyedMessage, Producer}
import org.apache.gearpump.TimeStamp
import org.apache.gearpump.streaming.transaction.api.OffsetStorage
import org.apache.gearpump.streaming.transaction.api.OffsetStorage.{Overflow, StorageEmpty, Underflow}
import org.apache.gearpump.util.LogUtil
import org.slf4j.Logger

import scala.util.{Try, Failure, Success}

object KafkaStorage {
  private val LOG: Logger = LogUtil.getLogger(classOf[KafkaStorage])

  def apply(config: KafkaConfig, topic: String, topicExists: Boolean, topicAndPartition: TopicAndPartition) = {
    val getConsumer = () => KafkaConsumer(topic, 0, config)
    val producer = new Producer[Array[Byte], Array[Byte]](KafkaUtil.buildProducerConfig(config))
    new KafkaStorage(topic, topicExists, producer, getConsumer())
  }
}

private[kafka] class KafkaStorage(topic: String,
                                  topicExists: Boolean,
                                  producer: Producer[Array[Byte], Array[Byte]],
                                  getConsumer: => KafkaConsumer) extends OffsetStorage {
  private val dataByTime: List[(TimeStamp, Array[Byte])] = {
    if (topicExists){
      load(getConsumer)
    } else {
      List.empty[(TimeStamp, Array[Byte])]
    }
  }

  /**
   * find data of max TimeStamp <= @param time
   * return Success(Array[Byte]) if the offset exists
   * return Failure(StorageEmpty) if no (TimeStamp, Array[Byte]) stored
   * return Failure(Overflow(max Array[Byte])) if @param time > max TimeStamp
   * return Failure(Underflow(min Array[Byte])) if @param time < min TimeStamp
   */
  override def lookUp(time: TimeStamp): Try[Array[Byte]] = {
    if (dataByTime.isEmpty) {
      Failure(StorageEmpty)
    } else {
      val min = dataByTime.head
      val max = dataByTime.last
      if (time < min._1) {
        Failure(Underflow(min._2))
      } else if (time > max._1) {
        Failure(Overflow(max._2))
      } else {
        Success(dataByTime.reverse.find(_._1 <= time).get._2)
      }
    }
  }

  override def append(time: TimeStamp, offset: Array[Byte]): Unit = {
    val message = new KeyedMessage[Array[Byte], Array[Byte]](
      topic, Injection[Long, Array[Byte]](time), 0, offset)
    producer.send(message)
  }

  override def close(): Unit = {
    producer.close()
  }

  private[kafka] def load(consumer: KafkaConsumer): List[(TimeStamp, Array[Byte])] = {
    @annotation.tailrec
    def fetch(offsets: List[(TimeStamp, Array[Byte])]): List[(TimeStamp, Array[Byte])] = {
      if (consumer.hasNext) {
        val kafkaMsg = consumer.next
        val offset = kafkaMsg.key.map { k =>
          Injection.invert[TimeStamp, Array[Byte]](k) match {
            case Success(time) => (time, kafkaMsg.msg)
            case Failure(e) => throw e
          }
        } orElse (throw new RuntimeException("offset key should not be null"))
        fetch(offsets :+ offset.get)
      } else {
        consumer.close()
        offsets
      }
    }
    fetch(List.empty[(TimeStamp, Array[Byte])])
  }

}

