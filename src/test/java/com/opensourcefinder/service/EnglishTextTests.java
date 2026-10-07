package com.opensourcefinder.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EnglishTextTests {

	@Test
	void acceptsEnglishProse() {
		assertThat(EnglishText.isLikelyEnglish("""
				When the configuration file is missing, the CLI crashes with a stack trace instead of printing a \
				helpful message. It should explain which file it was looking for and how to create one.""")).isTrue();
	}

	@Test
	void acceptsShortTechnicalText() {
		assertThat(EnglishText.isLikelyEnglish("NPE in FooBarFactory#create")).isTrue();
		assertThat(EnglishText.isLikelyEnglish("cmd/go: improve error message for invalid module path")).isTrue();
	}

	@Test
	void acceptsTextTooShortToJudge() {
		assertThat(EnglishText.isLikelyEnglish(null)).isTrue();
		assertThat(EnglishText.isLikelyEnglish("")).isTrue();
		assertThat(EnglishText.isLikelyEnglish("🚀 v2")).isTrue();
	}

	@Test
	void toleratesAQuotedNonEnglishString() {
		assertThat(EnglishText.isLikelyEnglish(
				"The Chinese translation of the settings button (设置) is cut off on small screens")).isTrue();
	}

	@Test
	void acceptsEnglishWithTranslation() {
		// Shortened from alibaba/nacos#2272.
		assertThat(EnglishText.isLikelyEnglish("""
				When Dubbo is registered, it is registered according to the interface. If you want to logoff all \
				interfaces provided by a service, it's too painful to search for only one interface, then enter the \
				details, and then go offline. If you can provide a query service based on IP and keywords, it's good \
				to close in batch.
				PS: dubbo注册的时候，是按照接口注册的。如果要下线一个服务提供的所有接口。只能一个接口一个接口的搜索，\
				再进入详情，然后下线，这个太痛苦了。如果能提供一个按照ip和关键字查询服务，批量进行关闭就好了。""")).isTrue();
	}

	@Test
	void rejectsChinese() {
		assertThat(EnglishText.isLikelyEnglish("修复登录页面在移动端显示错位的问题")).isFalse();
	}

	@Test
	void rejectsChineseMixedWithIdentifiers() {
		assertThat(EnglishText.isLikelyEnglish("修复 NullPointerException 问题，在调用 UserService 时出现")).isFalse();
	}

	@Test
	void rejectsOtherNonLatinScripts() {
		assertThat(EnglishText.isLikelyEnglish("Добавить поддержку тёмной темы в настройках")).isFalse();
		assertThat(EnglishText.isLikelyEnglish("設定画面にダークモードを追加する")).isFalse();
		assertThat(EnglishText.isLikelyEnglish("설정 화면에 다크 모드 추가")).isFalse();
	}

	@Test
	void rejectsLongerLatinScriptTextInAnotherLanguage() {
		assertThat(EnglishText.isLikelyEnglish("""
				Cuando falta el archivo de configuración, la herramienta falla con un error en lugar de mostrar un \
				mensaje útil. Debería explicar qué archivo estaba buscando y cómo crear uno nuevo desde cero.""")).isFalse();
	}

	@Test
	void ignoresCodeInHtml() {
		assertThat(EnglishText.isLikelyEnglishHtml("""
				<p>登录页面在移动端显示错位，请修复。</p>
				<pre><code>public void login(User user) { validate(user); session.start(user); }</code></pre>
				""")).isFalse();
		assertThat(EnglishText.isLikelyEnglishHtml("""
				<p>The comment in this snippet should be translated.</p>
				<pre><code>// 初始化用户会话并验证凭据</code></pre>
				""")).isTrue();
	}
}
