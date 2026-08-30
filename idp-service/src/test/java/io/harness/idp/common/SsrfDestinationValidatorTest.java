/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.Test;

public class SsrfDestinationValidatorTest {
  @Test
  public void blocksLoopbackLinkLocalAndPrivateIpv4() throws UnknownHostException {
    assertThat(blocked("127.0.0.1")).isTrue();
    assertThat(blocked("169.254.169.254")).isTrue(); // cloud metadata
    assertThat(blocked("10.0.0.1")).isTrue();
    assertThat(blocked("172.16.5.5")).isTrue();
    assertThat(blocked("192.168.1.1")).isTrue();
    assertThat(blocked("100.64.0.1")).isTrue(); // CGNAT
    assertThat(blocked("0.0.0.0")).isTrue();
  }

  @Test
  public void allowsPublicIpv4() throws UnknownHostException {
    assertThat(blocked("8.8.8.8")).isFalse();
    assertThat(blocked("34.117.59.81")).isFalse();
  }

  @Test
  public void blocksLoopbackLinkLocalAndUniqueLocalIpv6() throws UnknownHostException {
    assertThat(blocked("::1")).isTrue();
    assertThat(blocked("fe80::1")).isTrue();
    assertThat(blocked("fd00::1")).isTrue(); // RFC4193 unique local
  }

  @Test
  public void allowsPublicIpv6() throws UnknownHostException {
    assertThat(blocked("2606:4700:4700::1111")).isFalse();
  }

  @Test
  public void blocksIpv4TunnelledInsideIpv6() throws UnknownHostException {
    assertThat(blocked("::ffff:169.254.169.254")).isTrue(); // IPv4-mapped
    assertThat(blocked("::ffff:a9fe:a9fe")).isTrue(); // IPv4-mapped, hex form
    assertThat(blocked("64:ff9b::169.254.169.254")).isTrue(); // NAT64
    assertThat(blocked("2002:a9fe:a9fe::")).isTrue(); // 6to4
    assertThat(blocked("::ffff:8.8.8.8")).isFalse(); // IPv4-mapped, but the embedded address is public
  }

  private boolean blocked(String literal) throws UnknownHostException {
    return SsrfDestinationValidator.isBlockedAddress(InetAddress.getByName(literal));
  }
}
